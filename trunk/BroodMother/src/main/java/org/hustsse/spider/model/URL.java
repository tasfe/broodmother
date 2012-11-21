package org.hustsse.spider.model;

import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.text.Normalizer;

import org.hustsse.spider.exception.URLException;

import sun.nio.cs.ThreadLocalCoders;

/**
 *
 *
 * @author Administrator
 *
 */
public class URL {
	/*
	 * private static final int DEFAULT_HTTP_PORT = 80; private static final int
	 * DEFAULT_HTTPS_PORT = 443;
	 *
	 * private static final String PROTOCOL_HTTP = "http"; private static final
	 * String PROTOCOL_HTTPS = "https";
	 *
	 * private static final Map<String,Integer> PROTOCOL_PORT_MAP = new
	 * HashMap<String, Integer>(); static{ PROTOCOL_PORT_MAP.put(PROTOCOL_HTTP,
	 * DEFAULT_HTTP_PORT); PROTOCOL_PORT_MAP.put(PROTOCOL_HTTPS,
	 * DEFAULT_HTTPS_PORT); }
	 */


	private java.net.URL url;
	// url的形式： protocol://host:port/path?query#fragment,暂不考虑userinfo
	private String protocal;
	private String host;
	private String escapedHost;
	private String decodedHost;
	private Integer port;
	private String path;
	private String escapedPath;
	private String decodedPath;
	private String query;
	private String escapedQuery;
	private String decodedQuery;
	private String fragment;
	private String urlToStr;
	private String escapedUrlString; // escaped
	private String decodedUrlString; // decoded


	public static void main(String[] args) {
		URL u = new URL("http://a:b@www.a.com/a/b");
		URL f = new URL(u,"http://www.baidu.com/b/../c/d");

		System.out.println(u.getBackedURL().getPath());
	}

	/**
	 * 一些简化：
	 * 1. 忽略掉userinfo http://user:pwd@www.a.com == http://www.a.com
	 *
	 * @param url 可以是decoded形式如“中国”，也可以是escaped形式如“%E8%BF%99%E4%B8%AA”。
	 * 如果是decoded，getEscapedXXX()方法将返回编码之后的形式，getDecodedXXX()将返回原值；
	 * 如果是escaped，getEscapedXXX()方法将返回原值，getDecodedXXX()将返回解码之后的形式；
	 * getXXX()一直会返回原值。
	 */
	public URL(String url) {
		try {
			this.url = new java.net.URL(url);
		} catch (MalformedURLException e) {
			throw new URLException("不支持的协议：" + url, e);
		}
	}

	public URL(URL base, String relativeURL) {
		try {
			this.url = new java.net.URL(base.getBackedURL(), relativeURL);
		} catch (MalformedURLException e) {
			throw new URLException("未指定的协议或不支持的协议：" + relativeURL + "，base url：" + base, e);
		}
	}

	public synchronized String getHost() {
		if (host == null)
			host = url.getHost();
		return host;
	}

	public synchronized Integer getPort() {
		if (port == null) {
			port = url.getPort();
			if (port < 0) {
				port = url.getDefaultPort();
				assert port != -1;
			}
		}
		return port;
	}

	public synchronized String getPath() {
		if (path == null) {
			path = url.getPath();
		}
		return path;
	}

	public synchronized String getQuery() {
		if (query == null) {
			query = url.getQuery(); // http://foo.com/?返回空字符串；
									// http://foo.com/返回null
		}
		return query;
	}

	public synchronized String getEscapedHost() {
		if (escapedHost != null)
			return escapedHost;
		escapedHost = encode(getHost());
		return escapedHost;
	}

	public synchronized String getEscapedPath() {
		if (escapedPath != null)
			return escapedPath;
		escapedPath = encode(getPath());
		return escapedPath;
	}

	public synchronized String getEscapedQuery() {
		if (escapedQuery != null)
			return escapedQuery;
		escapedQuery = encode(getQuery());
		return escapedQuery;
	}

	public synchronized String getDecodedHost() {
		if (decodedHost != null)
			return decodedHost;
		decodedHost = decode(getHost());
		return decodedHost;
	}

	public synchronized String getDecodedPath() {
		if (decodedPath != null)
			return decodedPath;
		decodedPath = decode(getPath());
		return decodedPath;
	}

	public synchronized String getDecodedQuery() {
		if (decodedQuery != null)
			return decodedQuery;
		decodedQuery = decode(getQuery());
		return decodedQuery;
	}

	public synchronized String getProtocol() {
		if (protocal == null) {
			protocal = url.getProtocol();
		}
		return protocal;
	}

	private java.net.URL getBackedURL() {
		return url;
	}

	public synchronized String toString() {
		if (urlToStr == null)
			urlToStr = url.toString();
		return urlToStr;
	}

	public synchronized String toEscapedString() {
		if (escapedUrlString != null)
			return escapedUrlString;
		escapedUrlString = encode(toString());
		return escapedUrlString;
	}

	public synchronized String toDecodedString() {
		if (decodedUrlString != null)
			return decodedUrlString;
		decodedUrlString = decode(toString());
		return decodedUrlString;
	}

	/**
	 * encode/decode a string, borrowed from java.net.URI
	 *
	 * @param s
	 * @return
	 */
	private static String encode(String s) {
		if (s == null)
			return null;

		int n = s.length();
		if (n == 0)
			return s;

		// First check whether we actually need to encode
		for (int i = 0;;) {
			if (s.charAt(i) >= '\u0080')
				break;
			if (++i >= n)
				return s;
		}

		String ns = Normalizer.normalize(s, Normalizer.Form.NFC);
		ByteBuffer bb = null;
		try {
			bb = ThreadLocalCoders.encoderFor("UTF-8").encode(CharBuffer.wrap(ns));
		} catch (CharacterCodingException x) {
			assert false;
		}

		StringBuffer sb = new StringBuffer();
		while (bb.hasRemaining()) {
			int b = bb.get() & 0xff;
			if (b >= 0x80)
				appendEscape(sb, (byte) b);
			else
				sb.append((char) b);
		}
		return sb.toString();
	}

	private static void appendEscape(StringBuffer sb, byte b) {
		sb.append('%');
		sb.append(hexDigits[(b >> 4) & 0x0f]);
		sb.append(hexDigits[(b >> 0) & 0x0f]);
	}

	private final static char[] hexDigits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	private static String decode(String s) {
		if (s == null)
			return s;
		int n = s.length();
		if (n == 0)
			return s;
		if (s.indexOf('%') < 0)
			return s;

		StringBuffer sb = new StringBuffer(n);
		ByteBuffer bb = ByteBuffer.allocate(n);
		CharBuffer cb = CharBuffer.allocate(n);
		CharsetDecoder dec = ThreadLocalCoders.decoderFor("UTF-8").onMalformedInput(CodingErrorAction.REPLACE)
				.onUnmappableCharacter(CodingErrorAction.REPLACE);

		// This is not horribly efficient, but it will do for now
		char c = s.charAt(0);
		boolean betweenBrackets = false;

		for (int i = 0; i < n;) {
			assert c == s.charAt(i); // Loop invariant
			if (c == '[') {
				betweenBrackets = true;
			} else if (betweenBrackets && c == ']') {
				betweenBrackets = false;
			}
			if (c != '%' || betweenBrackets) {
				sb.append(c);
				if (++i >= n)
					break;
				c = s.charAt(i);
				continue;
			}
			bb.clear();
			for (;;) {
				assert (n - i >= 2);
				bb.put(decode(s.charAt(++i), s.charAt(++i)));
				if (++i >= n)
					break;
				c = s.charAt(i);
				if (c != '%')
					break;
			}
			bb.flip();
			cb.clear();
			dec.reset();
			CoderResult cr = dec.decode(bb, cb, true);
			assert cr.isUnderflow();
			cr = dec.flush(cb);
			assert cr.isUnderflow();
			sb.append(cb.flip().toString());
		}

		return sb.toString();
	}

	private static byte decode(char c1, char c2) {
		return (byte) (((decode(c1) & 0xf) << 4) | ((decode(c2) & 0xf) << 0));
	}

	private static int decode(char c) {
		if ((c >= '0') && (c <= '9'))
			return c - '0';
		if ((c >= 'a') && (c <= 'f'))
			return c - 'a' + 10;
		if ((c >= 'A') && (c <= 'F'))
			return c - 'A' + 10;
		assert false;
		return -1;
	}
}
