package org.hustsse.spider.dns;

import java.io.Serializable;
import java.net.InetAddress;

import org.hustsse.spider.util.InetAddressUtil;

/**
 * 简单的DNS类，表示一个URL所属的web server的地址。
 *
 * @author Anderson
 *
 */
public class Dns implements Serializable {
	private static final long serialVersionUID = -198822319L;
	/** ip地址 */
	private InetAddress ip;
	/** remote server */
	private String host;

	public static final int NEVER_EXPIRES = -1;

	/**
	 * TTL gotten from dns record.
	 *
	 * <p>
	 * -1:永不过期 0：不缓存 >0:缓存时间，秒
	 * </p>
	 *
	 * From rfc2035:
	 *
	 * <pre>
	 * TTL       a 32 bit unsigned integer that specifies the time
	 *           interval (in seconds) that the resource record may be
	 *           cached before it should be discarded.  Zero values are
	 *           interpreted to mean that the RR can only be used for the
	 *           transaction in progress, and should not be cached.
	 * </pre>
	 */
	private int ttl = NEVER_EXPIRES;

	public Dns(String host, int ttl) {
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
