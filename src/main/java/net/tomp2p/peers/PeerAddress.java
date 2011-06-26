/*
 * Copyright 2009 Thomas Bocek
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.tomp2p.peers;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * A PeerAddress contains the node ID and how to contact this node using both
 * TCP and UDP. This class is thread safe (or it does not matter if its not).
 * The serialized size of this class is for IPv4 (20 + 4 + 1 + 4=29), for IPv6
 * (20 + 4 + 1 + 16=41)
 * 
 * @author Thomas Bocek
 * 
 */
public final class PeerAddress implements Comparable<PeerAddress>, Serializable
{
	final private static int NET6 = 1;
	final private static int FIREWALL_UDP = 2;
	final private static int FIREWALL_TCP = 4;
	final private static int PRESET_IPv4 = 8;
	// final private static int RESERVED_1 = 16;
	// final private static int RESERVED_2 = 32;
	// final private static int RESERVED_3 = 64;
	// final private static int RESERVED_4 = 128;
	final private static long serialVersionUID = -1316622724169272306L;
	final private Number160 id;
	// a peer can change its IP, so this is not final.
	final private InetAddress address;
	final private int portUDP;
	final private int portTCP;
	// connection info
	final private boolean net6;
	final private boolean firewalledUDP;
	final private boolean firewalledTCP;
	final private boolean presetIPv4;
	// we can make the hash final as it never changes, and this class is used
	// multiple times in maps. A new peer is always added to the peermap, so
	// making this final saves CPU time. The maps used are removedPeerCache,
	// that is always checked, peerMap that is either added or checked if
	// already present. Also peers from the neighbor list sent over the wire are
	// added to the peermap.
	final private int hashCode;
	// if deserialized from a byte array using the constructor, then we need to
	// report how many data we processed.
	final private int offset;
	final private int readBytes;
	// one byte for type and two time two shorts for the ports, plus IP
	final public static int SIZE_IP_SOCKv6 = 1 + 4 + 16;
	final public static int SIZE_IP_SOCKv4 = 1 + 4 + 4;
	final public static int SIZE_IPv6 = SIZE_IP_SOCKv6 + Number160.BYTE_ARRAY_SIZE;
	final public static int SIZE_IPv4 = SIZE_IP_SOCKv4 + Number160.BYTE_ARRAY_SIZE;
	final public static PeerAddress EMPTY_IPv4;

	static
	{
		PeerAddress empty = null;
		try
		{
			empty = new PeerAddress(new byte[SIZE_IPv4]);
		}
		catch (UnknownHostException e)
		{
			// never happens, we set the correct size in this class
			e.printStackTrace();
		}
		EMPTY_IPv4 = empty;
	}

	/**
	 * Creates a new peeraddress, where the byte array has to be in the rigth
	 * format and in the right size. The new offset can be accessed with
	 * offset().
	 * 
	 * @param me
	 *            The serialized array
	 * @throws UnknownHostException
	 *             Using InetXAddress.getByAddress creates this exception.
	 */
	public PeerAddress(final byte[] me) throws UnknownHostException
	{
		this(me, 0);
	}

	/**
	 * Creates a PeerAddress from a continuous byte array. This is useful if you
	 * don't know the size beforehand. The new offset can be accessed with
	 * offset().
	 * 
	 * @param me
	 *            The serialized array
	 * @param offset
	 *            the offset, where to start
	 * @throws UnknownHostException
	 *             Using InetXAddress.getByAddress creates this exception.
	 */
	public PeerAddress(final byte[] me, int offset) throws UnknownHostException
	{
		// get the peer ID, this is independant of the type
		final int offsetOld = offset;
		final byte tmp[] = new byte[Number160.BYTE_ARRAY_SIZE];
		System.arraycopy(me, offset, tmp, 0, Number160.BYTE_ARRAY_SIZE);
		this.id = new Number160(tmp);
		offset += Number160.BYTE_ARRAY_SIZE;
		// get the type
		final int types = me[offset] & 0xff;
		this.net6 = (types & NET6) > 0;
		this.firewalledUDP = (types & FIREWALL_UDP) > 0;
		this.firewalledTCP = (types & FIREWALL_TCP) > 0;
		this.presetIPv4 = (types & PRESET_IPv4) > 0;
		offset++;
		// get the port for UDP and TCP
		this.portTCP = ((me[offset] & 0xff) << 8) + (me[offset + 1] & 0xff);
		this.portUDP = ((me[offset + 2] & 0xff) << 8) + (me[offset + 3] & 0xff);
		offset += 4;
		//
		final byte tmp2[];
		if (isIPv4())
		{
			// IPv4 is 32 bit
			tmp2 = new byte[4];
			System.arraycopy(me, offset, tmp2, 0, 4);
			this.address = Inet4Address.getByAddress(tmp2);
			offset += 4;
		}
		else
		{
			// IPv6 is 128 bit
			tmp2 = new byte[16];
			System.arraycopy(me, offset, tmp2, 0, 16);
			this.address = Inet6Address.getByAddress(tmp2);
			offset += 16;
		}
		this.readBytes = offset - offsetOld;
		this.offset = offset;
		this.hashCode = id.hashCode();
	}

	public PeerAddress(Number160 id, final byte[] me, int offset) throws UnknownHostException
	{
		final int offsetOld = offset;
		// get the peer ID, this is independant of the type
		this.id = id;
		// get the type
		final int types = me[offset] & 0xff;
		this.net6 = (types & NET6) > 0;
		this.firewalledUDP = (types & FIREWALL_UDP) > 0;
		this.firewalledTCP = (types & FIREWALL_TCP) > 0;
		this.presetIPv4 = (types & PRESET_IPv4) > 0;
		offset++;
		// get the port for UDP and TCP
		this.portTCP = ((me[offset] & 0xff) << 8) + (me[offset + 1] & 0xff);
		this.portUDP = ((me[offset + 2] & 0xff) << 8) + (me[offset + 3] & 0xff);
		offset += 4;
		//
		final byte tmp2[];
		if (isIPv4())
		{
			// IPv4 is 32 bit
			tmp2 = new byte[4];
			System.arraycopy(me, offset, tmp2, 0, 4);
			this.address = Inet4Address.getByAddress(tmp2);
			offset += 4;
		}
		else
		{
			// IPv6 is 128 bit
			tmp2 = new byte[16];
			System.arraycopy(me, offset, tmp2, 0, 16);
			this.address = Inet6Address.getByAddress(tmp2);
			offset += 16;
		}
		this.readBytes = offset - offsetOld;
		this.offset = offset;
		this.hashCode = id.hashCode();
	}

	/**
	 * The format of the peer address can also be split.
	 * 
	 * @param peerAddress
	 *            The 160bit number for the peer ID
	 * @param socketAddress
	 *            The socket address with type port and IP
	 * @throws UnknownHostException
	 */
	public PeerAddress(byte[] peerAddress, byte[] socketAddress) throws UnknownHostException
	{
		int offsetPeerAddress = 0, offsetSocketAddress = 0;
		int types = socketAddress[offsetSocketAddress] & 0xff;
		this.net6 = (types & NET6) > 0;
		this.firewalledUDP = (types & FIREWALL_UDP) > 0;
		this.firewalledTCP = (types & FIREWALL_TCP) > 0;
		this.presetIPv4 = (types & PRESET_IPv4) > 0;
		offsetSocketAddress++;
		// get the peer ID
		byte tmp[] = new byte[Number160.BYTE_ARRAY_SIZE];
		System.arraycopy(peerAddress, offsetPeerAddress, tmp, 0, Number160.BYTE_ARRAY_SIZE);
		this.id = new Number160(tmp);
		offsetPeerAddress += Number160.BYTE_ARRAY_SIZE;
		// get the port for UDP and TCP
		this.portTCP = ((socketAddress[offsetSocketAddress] & 0xff) << 8)
				+ (socketAddress[offsetSocketAddress + 1] & 0xff);
		this.portUDP = ((socketAddress[offsetSocketAddress + 2] & 0xff) << 8)
				+ (socketAddress[offsetSocketAddress + 3] & 0xff);
		offsetSocketAddress += 4;
		//
		if (isIPv4())
		{
			// IPv4 is 32 bit
			tmp = new byte[4];
			System.arraycopy(socketAddress, offsetSocketAddress, tmp, 0, 4);
			this.address = Inet4Address.getByAddress(tmp);
			offsetSocketAddress += 4;
		}
		else
		{
			// IPv6 is 128 bit
			tmp = new byte[16];
			System.arraycopy(socketAddress, offsetSocketAddress, tmp, 0, 16);
			this.address = Inet6Address.getByAddress(tmp);
			offsetSocketAddress += 16;
		}
		this.offset = offsetSocketAddress + offsetPeerAddress;
		// initial offset is 0, so readbytes == offset
		this.readBytes = offset;
		this.hashCode = id.hashCode();
	}

	/**
	 * This is usually used for debugging, the address will be null and ports -1
	 * 
	 * @param id
	 *            The id of the peer
	 */
	public PeerAddress(Number160 id)
	{
		this(id, null, -1, -1);
	}

	/**
	 * Creates a PeerAddress
	 * 
	 * @param id
	 *            The id of the peer
	 * @param address
	 *            The address of the peer, how to reach this peer
	 * @param portTCP
	 *            The tcp port how to reach the peer
	 * @param portUDP
	 *            The udp port how to reach the peer
	 */
	public PeerAddress(Number160 id, InetAddress address, int portTCP, int portUDP, boolean firewalledUDP,
			boolean firewalledTCP, boolean presetIPv4)
	{
		this.id = id;
		this.address = address;
		this.portTCP = portTCP;
		this.portUDP = portUDP;
		this.hashCode = id.hashCode();
		this.net6 = address instanceof Inet6Address;
		this.firewalledUDP = firewalledUDP;
		this.firewalledTCP = firewalledTCP;
		this.presetIPv4 = presetIPv4;
		// unused here
		this.offset = -1;
		this.readBytes = -1;
	}

	public PeerAddress(Number160 id, InetAddress address, int portTCP, int portUDP)
	{
		this(id, address, portTCP, portUDP, false, false, false);
	}

	public PeerAddress(Number160 id, InetSocketAddress inetSocketAddress)
	{
		this(id, inetSocketAddress.getAddress(), inetSocketAddress.getPort(), inetSocketAddress.getPort());
	}

	public PeerAddress(Number160 id, PeerAddress parent)
	{
		this(id, parent.address, parent.portTCP, parent.portUDP);
	}

	public PeerAddress(Number160 id, InetAddress address, int portTCP, int portUDP, byte optionType)
	{
		this(id, address, portTCP, portUDP, isFirewalledUDP(optionType), isFirewalledTCP(optionType),
				isPresetIPv4(optionType));
	}

	/**
	 * When deserializing, we need to know how much we deserialized from the
	 * constructor call.
	 * 
	 * @return The new offset
	 */
	public int offset()
	{
		return offset;
	}

	public int readBytes()
	{
		return readBytes;
	}

	/**
	 * Serializes to a new array with the proper size
	 * 
	 * @return The serialized representation.
	 */
	public byte[] toByteArray()
	{
		byte[] me;
		if (address instanceof Inet4Address)
			me = new byte[SIZE_IPv4];
		else
			me = new byte[SIZE_IPv6];
		toByteArray(me, 0);
		return me;
	}

	/**
	 * Serializes to an existing array.
	 * 
	 * @param me
	 *            The array where the result should be stored
	 * @param offset
	 *            The offset where to start to save the result in the byte array
	 * @return The new offest.
	 */
	public int toByteArray(byte[] me, int offset)
	{
		// save the peer id
		int newOffset = id.toByteArray(me, offset);
		return toByteArraySocketAddress(me, newOffset);
	}

	/**
	 * Please use toByteArraySocketAddress
	 * 
	 * @return
	 */
	@Deprecated
	public byte[] getSocketAddress()
	{
		return toByteArraySocketAddress();
	}

	public byte[] toByteArraySocketAddress()
	{
		byte[] me = new byte[getSocketAddressSize()];
		getSocketAddress(me, 0);
		return me;
	}

	/**
	 * Please use toByteArraySocketAddress
	 * 
	 * @return
	 */
	@Deprecated
	public int getSocketAddress(byte[] me, int offset)
	{
		return toByteArraySocketAddress(me, offset);
	}

	public int toByteArraySocketAddress(byte[] me, int offset)
	{
		// save the type
		me[offset] = createType();
		int delta = 1;
		// save ports tcp
		int tmp = offset + delta;
		me[tmp + 0] = (byte) (portTCP >>> 8);
		me[tmp + 1] = (byte) portTCP;
		me[tmp + 2] = (byte) (portUDP >>> 8);
		me[tmp + 3] = (byte) portUDP;
		delta += 4;
		// save the ip address
		if (address instanceof Inet4Address)
		{
			System.arraycopy(address.getAddress(), 0, me, offset + delta, 4);
			delta += 4;
		}
		else
		{
			System.arraycopy(address.getAddress(), 0, me, offset + delta, 16);
			delta += 16;
		}
		return offset + delta;
	}

	/**
	 * Returns the address or null if no address set
	 * 
	 * @return The address of this peer
	 */
	public InetAddress getInetAddress()
	{
		return address;
	}

	/**
	 * Returns the socket address. The socket address will be created if
	 * necessary.
	 * 
	 * @return The socket address how to reach this peer
	 */
	public InetSocketAddress createSocketTCP()
	{
		return new InetSocketAddress(address, portTCP);
	}

	/**
	 * Returns the socket address. The socket address will be created if
	 * necessary.
	 * 
	 * @return The socket address how to reach this peer
	 */
	public InetSocketAddress createSocketUDP()
	{
		return new InetSocketAddress(address, portUDP);
	}

	/**
	 * The id of the peer. A peer cannot change its id.
	 * 
	 * @return Id of the peer
	 */
	public Number160 getID()
	{
		return id;
	}

	public byte createType()
	{
		byte result = 0;
		if (net6)
			result |= NET6;
		if (firewalledUDP)
			result |= FIREWALL_UDP;
		if (firewalledTCP)
			result |= FIREWALL_TCP;
		return result;
	}

	public static boolean isNet6(int type)
	{
		return ((type & 0xff) & NET6) > 0;
	}

	public static boolean isFirewalledTCP(int type)
	{
		return ((type & 0xff) & FIREWALL_TCP) > 0;
	}

	public static boolean isFirewalledUDP(int type)
	{
		return ((type & 0xff) & FIREWALL_UDP) > 0;
	}

	public static boolean isPresetIPv4(int type)
	{
		return ((type & 0xff) & PRESET_IPv4) > 0;
	}

	public static int expectedLength(int type)
	{
		if (isNet6(type))
			return SIZE_IPv6;
		else
			return SIZE_IPv4;
	}

	public static int expectedSocketLength(int type)
	{
		if (isNet6(type))
			return SIZE_IP_SOCKv6;
		else
			return SIZE_IP_SOCKv4;
	}

	public int expectedLength()
	{
		return isIPv6() ? SIZE_IPv6 : SIZE_IPv4;
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder("PeerAddr[");
		return sb.append(address).append(",udp:").append(portUDP).append(",tcp:").append(portTCP).append(",ID:")
				.append(id.toString()).append("]").toString();
	}

	@Override
	public int compareTo(PeerAddress nodeAddress)
	{
		// the id determines if two peer are equal, the address does not matter
		return id.compareTo(nodeAddress.id);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj instanceof PeerAddress)
			return id.equals(((PeerAddress) obj).id);
		else
			return false;
	}

	@Override
	public int hashCode()
	{
		// dont calculate all the time, only once.
		return this.hashCode;
	}

	/**
	 * @return TCP port
	 */
	public int portTCP()
	{
		return portTCP;
	}

	/**
	 * @return UDP port
	 */
	public int portUDP()
	{
		return portUDP;
	}

	public boolean isFirewalledUDP()
	{
		return firewalledUDP;
	}

	public boolean isFirewalledTCP()
	{
		return firewalledTCP;
	}

	public boolean isIPv6()
	{
		return net6;
	}

	public boolean isIPv4()
	{
		return !net6;
	}

	public boolean isPresetIPv4()
	{
		return presetIPv4;
	}

	public PeerAddress changeFirewalledUDP(boolean status)
	{
		return new PeerAddress(id, address, portTCP, portUDP, status, firewalledTCP, presetIPv4);
	}

	public PeerAddress changeFirewalledTCP(boolean status)
	{
		return new PeerAddress(id, address, portTCP, portUDP, firewalledUDP, status, presetIPv4);
	}

	public PeerAddress changePorts(int portUDP, int portTCP)
	{
		return new PeerAddress(id, address, portTCP, portUDP, firewalledUDP, firewalledTCP, presetIPv4);
	}

	public PeerAddress changePeerId(Number160 id2)
	{
		return new PeerAddress(id2, address, portTCP, portUDP, firewalledUDP, firewalledTCP, presetIPv4);
	}

	public PeerAddress changePresetIPv4(boolean status)
	{
		return new PeerAddress(id, address, portTCP, portUDP, firewalledUDP, firewalledTCP, status);
	}

	public int getSocketAddressSize()
	{
		return (address instanceof Inet4Address) ? SIZE_IP_SOCKv4 : SIZE_IP_SOCKv6;
	}
}
