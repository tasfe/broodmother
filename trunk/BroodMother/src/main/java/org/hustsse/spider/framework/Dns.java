package org.hustsse.spider.framework;

import java.io.Serializable;
import java.net.InetAddress;

import org.hustsse.spider.util.InetAddressUtil;

public class Dns implements Serializable {
	private static final long serialVersionUID = -198822319L;

	private InetAddress ip;
	private String countryCode;
	private String host;

	public static final int NEVER_EXPIRES = -1;

	/**
	 * -1:永不过期
	 * 0：不缓存
	 * >0:缓存时间，秒
	 *
     * TTL gotten from dns record.
     *
     * From rfc2035:
     * <pre>
     * TTL       a 32 bit unsigned integer that specifies the time
     *           interval (in seconds) that the resource record may be
     *           cached before it should be discarded.  Zero values are
     *           interpreted to mean that the RR can only be used for the
     *           transaction in progress, and should not be cached.
     * </pre>
     */
	private int ttl = NEVER_EXPIRES;

	public Dns(String host,int ttl) {
		this.host = host;
		this.ttl = ttl;
		this.ip = InetAddressUtil.getIPHostAddress(host);
	}

	public Dns(String host, InetAddress ip, int ttl) {
		this.host = host;
		this.ip = ip;
		this.ttl = ttl;
	}

	public InetAddress getIp() {
		return ip;
	}
	public void setIp(InetAddress ip) {
		this.ip = ip;
	}
	public String getCountryCode() {
		return countryCode;
	}
	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
	}
	public int getTtl() {
		return ttl;
	}
	public void setTtl(int ttl) {
		this.ttl = ttl;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}
}
