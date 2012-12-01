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

import java.util.Map;

import org.hustsse.spider.util.StringUtil;

/**
 * The default {@link HttpRequest} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2088 $, $Date: 2010-01-27 11:38:17 +0900 (Wed, 27 Jan 2010) $
 */
public class DefaultHttpRequest extends DefaultHttpMessage implements HttpRequest {

    private HttpMethod method;
    private String uri;

    /**
     * Creates a new instance.
     *
     * @param httpVersion the HTTP version of the request
     * @param method      the HTTP method of the request
     * @param uri         the URI or path of the request
     */
    public DefaultHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri) {
        super(httpVersion);
        setMethod(method);
        setUri(uri);
    }

    public HttpMethod getMethod() {
        return method;
    }

    public void setMethod(HttpMethod method) {
        if (method == null) {
            throw new NullPointerException("method");
        }
        this.method = method;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        if (uri == null) {
            throw new NullPointerException("uri");
        }
        this.uri = uri;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append("(chunked: ");
        buf.append(isChunked());
        buf.append(')');
        buf.append(StringUtil.NEWLINE);
        buf.append(getMethod().toString());
        buf.append(' ');
        buf.append(getUri());
        buf.append(' ');
        buf.append(getProtocolVersion().getText());
        buf.append(StringUtil.NEWLINE);
        appendHeaders(buf);

        // Remove the last newline.
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }

	@Override
	public String toRequestStr() {
		//构造请求头
		StringBuilder buf = new StringBuilder();
		buf.append(getMethod().toString());
		buf.append(' ');
		buf.append(getUri());
		buf.append(' ');
		buf.append(getProtocolVersion().getText());
		appendCRLF(buf);
		//构造 header
		for (Map.Entry<String, String> e: getHeaders()) {
            buf.append(e.getKey());
            buf.append(": ");
            buf.append(e.getValue());
            appendCRLF(buf);
        }

		appendCRLF(buf);//HTTP协议要求与正文之间两个CRLF，这里补上一个
		return buf.toString();
	}

	private void appendCRLF(StringBuilder buf) {
		/*
		 * 不用StringUtil.newLine，是因为后者是平台相关的，而HTTP协议规定是两个字符，所以这里定死。
		 * 当使用ASCII字符集编码时就能得到正确的结果了。
		 */
//		buf.append((char)HttpCodecUtil.CR);
//		buf.append((char)HttpCodecUtil.LF);
		buf.append("\r\n");
	}
}
