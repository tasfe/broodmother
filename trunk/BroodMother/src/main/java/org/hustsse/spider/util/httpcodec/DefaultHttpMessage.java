/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.hustsse.spider.util.httpcodec;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hustsse.spider.util.StringUtil;

/**
 * The default {@link HttpMessage} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2088 $, $Date: 2010-01-27 11:38:17 +0900 (Wed, 27 Jan 2010) $
 */
public class DefaultHttpMessage implements HttpMessage {

	private final HttpHeaders headers = new HttpHeaders();
	private HttpVersion version;
	// private ByteBuffer content = ByteBuffers.EMPTY_BUFFER;
	private ByteBuffer content = null;
	private boolean chunked;

	static final String DEFAULT_CONTENT_CHARSET = "UTF-8";
	// content字符集
	private String contentCharset;
	// content bytebuffer decode之后的字符串
	private String contentStr;

	/**
	 * Creates a new instance.
	 */
	protected DefaultHttpMessage(final HttpVersion version) {
		setProtocolVersion(version);
	}

	public void addHeader(final String name, final Object value) {
		headers.addHeader(name, value);
	}

	public void setHeader(final String name, final Object value) {
		headers.setHeader(name, value);
	}

	public void setHeader(final String name, final Iterable<?> values) {
		headers.setHeader(name, values);
	}

	public void removeHeader(final String name) {
		headers.removeHeader(name);
	}

	@Deprecated
	public long getContentLength() {
		return HttpHeaders.getContentLength(this);
	}

	@Deprecated
	public long getContentLength(long defaultValue) {
		return HttpHeaders.getContentLength(this, defaultValue);
	}

	public boolean isChunked() {
		if (chunked) {
			return true;
		} else {
			return HttpCodecUtil.isTransferEncodingChunked(this);
		}
	}

	public void setChunked(boolean chunked) {
		this.chunked = chunked;
		if (chunked) {
			// setContent(ByteBuffers.EMPTY_BUFFER);
			setContent(null);
		}
	}

	@Deprecated
	public boolean isKeepAlive() {
		return HttpHeaders.isKeepAlive(this);
	}

	public void clearHeaders() {
		headers.clearHeaders();
	}

	public void setContent(ByteBuffer content) {
		// if (content == null) {
		// content = ByteBuffers.EMPTY_BUFFER;
		// }
		// if (content.readable() && isChunked()) {
		// throw new IllegalArgumentException(
		// "non-empty content disallowed if this.chunked == true");
		// }
		this.content = content;
	}

	public String getHeader(final String name) {
		List<String> values = getHeaders(name);
		return values.size() > 0 ? values.get(0) : null;
	}

	public List<String> getHeaders(final String name) {
		return headers.getHeaders(name);
	}

	public List<Map.Entry<String, String>> getHeaders() {
		return headers.getHeaders();
	}

	public boolean containsHeader(final String name) {
		return headers.containsHeader(name);
	}

	public Set<String> getHeaderNames() {
		return headers.getHeaderNames();
	}

	public HttpVersion getProtocolVersion() {
		return version;
	}

	public void setProtocolVersion(HttpVersion version) {
		if (version == null) {
			throw new NullPointerException("version");
		}
		this.version = version;
	}

	/**
	 *
	 */
	public ByteBuffer getContent() {
		return content.duplicate();
	}

	public static void main(String[] args) {
		String contentStr = "<meta content=\"text/html; charset=gb2312\" http-equiv=\"Content-Type\">\n" +
				"";
		String contentCharset;
		Matcher matchMeta = charsetMetaPattern.matcher(contentStr);
		boolean foundMeta = matchMeta.find();// 只查找一次meta标签
		if (foundMeta) {
			String meta = matchMeta.group();
			// find the "charset" or "content" attr
			Matcher matchCharset = charsetAttrPattern.matcher(meta);
			boolean foundCharset = matchCharset.find();
			if (foundCharset) {
				// get the charset
				String charsetAttr = matchCharset.group();
				String s = charsetAttr.split("=")[1];
				if (s.startsWith("'") || s.startsWith("\""))
					s = s.substring(1);
				if (s.endsWith("'") || s.endsWith("\""))
					s = s.substring(0, s.length() - 1);
				contentCharset = s;

				// decode content & return
				System.out.println(contentCharset);
			}
		}
	}

	// <meta http-equiv="Content-Type" content="text/html;charset=gb2312">
	// <meta charset="gb2312">
	static Pattern charsetMetaPattern = Pattern.compile("<\\s*meta[^>]*content\\s*=[^>]*>" + "|<\\s*meta[^>]*charset\\s*=[^>]*>",
			Pattern.CASE_INSENSITIVE);
	static Pattern charsetAttrPattern = Pattern.compile("charset\\s*=\\s*[a-zA-Z0-9]+['\"]", Pattern.CASE_INSENSITIVE);

	public String getContentStr() {
		if (contentStr != null)
			return contentStr;

		// 优先使用content type中指定的字符集
		String charsetInHeader = getContentCharsetFromHeader();
		if (charsetInHeader != null) {
			this.contentCharset = charsetInHeader;
			Charset c = Charset.forName(charsetInHeader);
			contentStr = c.decode(getContent()).toString();
			return contentStr;
		}

		/*
		 * header中未指定charset时，尝试使用默认字符集解析content并查找META标签 <meta
		 * http-equiv="Content-Type" content="text/html;charset=gb2312"> 或 HTML5
		 * <meta charset="gb2312">。若查找到了，则重新对content解析。
		 */
		Charset c = Charset.forName(DEFAULT_CONTENT_CHARSET);
		contentStr = c.decode(getContent()).toString();
		// find the "meta" html tag
		Matcher matchMeta = charsetMetaPattern.matcher(contentStr);
		boolean foundMeta = matchMeta.find();// 只查找一次meta标签
		if (foundMeta) {
			String meta = matchMeta.group();
			// find the "charset" or "content" attr
			Matcher matchCharset = charsetAttrPattern.matcher(meta);
			boolean foundCharset = matchCharset.find();
			if (foundCharset) {
				// get the charset
				String charsetAttr = matchCharset.group();
				String s = charsetAttr.split("=")[1];
				if (s.startsWith("'") || s.startsWith("\""))
					s = s.substring(1);
				if (s.endsWith("'") || s.endsWith("\""))
					s = s.substring(0, s.length() - 1);
				this.contentCharset = s;

				// decode content & return
				Charset foundCharsetInMeta = Charset.forName(s);
				contentStr = foundCharsetInMeta.decode(getContent()).toString();
				return contentStr;
			}
		}
		/*
		 * if no charset info in http headers, either no "meta" tag with charset
		 * info in the html, use the default charset decode and return. This is
		 * different with the browser,which will cache some bytes of the http
		 * content, and then "guess" the charset used. See:
		 * http://www.cnblogs.com/haichuan3000/articles/2147907.html
		 */
		return contentStr;
	}

	/**
	 * 得到content使用字符集，注意，返回的字符集从content-type头解析而来。
	 * 若没有在content-type中指定，则返回null，不会自动根据content判断所 用字符集。
	 */
	private String getContentCharsetFromHeader() {
		String contentType = HttpHeaders.getHeader(this, HttpHeaders.Names.CONTENT_TYPE);
		if (contentType != null && !contentType.isEmpty()) {
			String[] splited = contentType.split(";");
			if (splited != null && splited.length >= 2) {
				String[] splited2 = splited[1].split("=");
				if (splited2 != null && splited2.length >= 2) {
					return splited2[1].toUpperCase();
				}
			}
		}
		return null;
	}

	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append(getClass().getSimpleName());
		buf.append("(version: ");
		buf.append(getProtocolVersion().getText());
		buf.append(", keepAlive: ");
		buf.append(isKeepAlive());
		buf.append(", chunked: ");
		buf.append(isChunked());
		buf.append(')');
		buf.append(StringUtil.NEWLINE);
		appendHeaders(buf);

		// Remove the last newline.
		buf.setLength(buf.length() - StringUtil.NEWLINE.length());
		return buf.toString();
	}

	void appendHeaders(StringBuilder buf) {
		for (Map.Entry<String, String> e : getHeaders()) {
			buf.append(e.getKey());
			buf.append(": ");
			buf.append(e.getValue());
			buf.append(StringUtil.NEWLINE);
		}
	}
}
