package org.hustsse.spider.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import org.hustsse.spider.util.httpcodec.DefaultHttpResponse;
import org.hustsse.spider.util.httpcodec.HttpCodecUtil;
import org.hustsse.spider.util.httpcodec.HttpHeaders;
import org.hustsse.spider.util.httpcodec.HttpRequest;
import org.hustsse.spider.util.httpcodec.HttpResponse;
import org.hustsse.spider.util.httpcodec.HttpResponseStatus;
import org.hustsse.spider.util.httpcodec.HttpVersion;

public class HttpMessageUtil {
	/**
	 * 编码Http请求。
	 *
	 * <p>
	 * 注意HTTP message是个文本协议，除了正文的字符集是在Header中指定，其他都是用US-ASCII字符集进行传输的，
	 * 注意区分charset和encoding的区别。
	 * <p>
	 * Http message中的“CRLF”，是ASCII字符集中的两个字符，分别对应“Carriage Return(回车)”和“Line Feed
	 * (换行)”，这二者的含义不同，具体见{@link HttpCodecUtil#CR}和{@link HttpCodecUtil#LF}
	 * <p>
	 * 各个平台上显示或者保存文本时的line seperator用什么字符是平台相关的，比如Unix系统里，每行结尾只有“换行”，即“\n”；
	 * Windows系统里，每行结尾是“回车换行”，即“\r\n”；Mac系统里，每行结尾是“回车”，即"\r"。 <br>
	 * 可以用如下方式得到平台相关的line seperator:
	 *
	 * <pre>
	 * <code>
	 *   newLine = new Formatter().format("%n").toString()
	 * </code>
	 * </pre>
	 *
	 * <h2>URL的encoding</h2>
	 * <p>
	 * URL最终作为Http请求行被发送时，由于发送时采用ASCII字符集，如何处理如中文这种字符？答案是使用一个ASCII的超集对其
	 * 进行“百分号Encoding”，标准推荐使用UTF-8。当用户在浏览器中输入url时，会使用浏览器默认字符集对其encoding。
	 *
	 * @param request
	 * @return
	 * @see StringUtil#NEWLINE
	 * @throws CharacterCodingException
	 */
	public static ByteBuffer encodeHttpRequest(HttpRequest request) throws CharacterCodingException {
		Charset charset = Charset.forName("ASCII"); // HTTP协议使用ASCII字符集进行传输
		CharsetEncoder encoder = charset.newEncoder();
		String requestStr = request.toRequestStr();
		return encoder.encode(CharBuffer.wrap(requestStr));
	}


	protected static enum State {
		EMPTY_CONTENT, READ_VARIABLE_LENGTH_CONTENT, READ_FIXED_LENGTH_CONTENT,
	}

	/**
	 * 解码http响应。
	 *
	 * <p>
	 * 如果响应没有正文，返回的结果{@link HttpResponse#getContent()}将返回null。
	 * 否则将返回一个已经flip为写出模式的ByteBuffer对象。
	 *
	 * @param merged
	 * @return
	 */
	public static HttpResponse decodeHttpResponse(ByteBuffer merged) {
		// response 第一行
		String firstline = readLine(merged);
		String[] splitedInitialLine = splitInitialLine(firstline);
		if (!firstline.toLowerCase().startsWith("http"))
			System.out.println("=============" + firstline);
		HttpResponse r = new DefaultHttpResponse(HttpVersion.valueOf(splitedInitialLine[0]), new HttpResponseStatus(
				Integer.valueOf(splitedInitialLine[1]), splitedInitialLine[2]));
		// headers
		State nextStep = readHeaders(r, merged);
		// contents
		switch (nextStep) {
		case EMPTY_CONTENT:
			// No content is expected.
			// Remove the headers which are not supposed to be present not
			// to confuse subsequent handlers.
			r.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
			break;
		case READ_FIXED_LENGTH_CONTENT:
			// we have a content-length so we just read the correct number of
			// bytes
			readFixedLengthContent(merged, r);
			break;
		case READ_VARIABLE_LENGTH_CONTENT:
			readVariableLengthContent(merged, r);
			break;
		default:
		}
		return r;
	}

	private static void readVariableLengthContent(ByteBuffer merged, HttpResponse r) {
		ByteBuffer content = merged.slice();
		r.setContent(content);
	}

	private static void readFixedLengthContent(ByteBuffer buffer, HttpResponse message) {
		long length = HttpHeaders.getContentLength(message, -1);
		assert length <= Integer.MAX_VALUE;
		int contentEndPostion = buffer.position() + (int) length;
		// 读取完毕，但内容不全 TODO 这么处理合适么？
		if(contentEndPostion > buffer.capacity()) {
			readVariableLengthContent(buffer, message);
			return;
		}

		// 为了防止拷贝，response的content与merged底层使用同一个数据结构，merged
		// = 响应头 + headers
		// +正文，前两者的内容不会太多，
		buffer.limit(contentEndPostion);
		ByteBuffer content = buffer.slice();
		message.setContent(content);
	}

	/**
	 * 根据响应的status code判断是否有content
	 *
	 * @param msg
	 * @return
	 */
	private static boolean isContentAlwaysEmpty(HttpResponse msg) {
		HttpResponse res = (HttpResponse) msg;
		int code = res.getStatus().getCode();
		if (code < 200) {
			return true;
		}
		switch (code) {
		case 204:
		case 205:
		case 304:
			return true;
		}
		return false;
	}

	private static String readLine(ByteBuffer buffer) {
		StringBuilder sb = new StringBuilder(64);
		while (true) {
			byte nextByte = buffer.get();
			if (nextByte == HttpCodecUtil.CR) {
				nextByte = buffer.get();
				if (nextByte == HttpCodecUtil.LF) {
					return sb.toString();
				}
			} else if (nextByte == HttpCodecUtil.LF) {
				return sb.toString();
			} else {
				sb.append((char) nextByte);
			}
		}
	}

	private static String[] splitInitialLine(String sb) {
		int aStart;
		int aEnd;
		int bStart;
		int bEnd;
		int cStart;
		int cEnd;

		aStart = findNonWhitespace(sb, 0);
		aEnd = findWhitespace(sb, aStart);

		bStart = findNonWhitespace(sb, aEnd);
		bEnd = findWhitespace(sb, bStart);

		cStart = findNonWhitespace(sb, bEnd);
		cEnd = findEndOfString(sb);

		return new String[] { sb.substring(aStart, aEnd), sb.substring(bStart, bEnd), cStart < cEnd ? sb.substring(cStart, cEnd) : "" };
	}

	private static int findNonWhitespace(String sb, int offset) {
		int result;
		for (result = offset; result < sb.length(); result++) {
			if (!Character.isWhitespace(sb.charAt(result))) {
				break;
			}
		}
		return result;
	}

	private static int findWhitespace(String sb, int offset) {
		int result;
		for (result = offset; result < sb.length(); result++) {
			if (Character.isWhitespace(sb.charAt(result))) {
				break;
			}
		}
		return result;
	}

	private static int findEndOfString(String sb) {
		int result;
		for (result = sb.length(); result > 0; result--) {
			if (!Character.isWhitespace(sb.charAt(result - 1))) {
				break;
			}
		}
		return result;
	}

	private static State readHeaders(HttpResponse message, ByteBuffer buffer) {
		String line = readHeader(buffer);
		String name = null;
		String value = null;
		if (line.length() != 0) {
			message.clearHeaders();
			do {
				char firstChar = line.charAt(0);
				if (name != null && (firstChar == ' ' || firstChar == '\t')) {
					value = value + ' ' + line.trim();
				} else {
					if (name != null) {
						message.addHeader(name, value);
					}
					String[] header = splitHeader(line);
					name = header[0];
					value = header[1];
				}

				line = readHeader(buffer);
			} while (line.length() != 0);

			// Add the last header.
			if (name != null) {
				message.addHeader(name, value);
			}
		}

		State nextStep;
		// 判断状态，决定下一步要怎么解析

		// 采用的协议是http1.0，因此不考虑chunked情况
		if (isContentAlwaysEmpty(message)) {
			nextStep = State.EMPTY_CONTENT;
		} else if (HttpHeaders.getContentLength(message, -1) >= 0) {
			nextStep = State.READ_FIXED_LENGTH_CONTENT;
		} else {
			nextStep = State.READ_VARIABLE_LENGTH_CONTENT;
		}
		return nextStep;
	}

	private static String readHeader(ByteBuffer buffer) {
		StringBuilder sb = new StringBuilder(64);
		loop: for (;;) {
			char nextByte = (char) buffer.get();

			switch (nextByte) {
			case HttpCodecUtil.CR:
				nextByte = (char) buffer.get();
				if (nextByte == HttpCodecUtil.LF) {
					break loop;
				}
				break;
			case HttpCodecUtil.LF:
				break loop;
			}
			sb.append(nextByte);
		}

		return sb.toString();
	}

	private static String[] splitHeader(String sb) {
		final int length = sb.length();
		int nameStart;
		int nameEnd;
		int colonEnd;
		int valueStart;
		int valueEnd;

		nameStart = findNonWhitespace(sb, 0);
		for (nameEnd = nameStart; nameEnd < length; nameEnd++) {
			char ch = sb.charAt(nameEnd);
			if (ch == ':' || Character.isWhitespace(ch)) {
				break;
			}
		}

		for (colonEnd = nameEnd; colonEnd < length; colonEnd++) {
			if (sb.charAt(colonEnd) == ':') {
				colonEnd++;
				break;
			}
		}

		valueStart = findNonWhitespace(sb, colonEnd);
		if (valueStart == length) {
			return new String[] { sb.substring(nameStart, nameEnd), "" };
		}

		valueEnd = findEndOfString(sb);
		return new String[] { sb.substring(nameStart, nameEnd), sb.substring(valueStart, valueEnd) };
	}
}
