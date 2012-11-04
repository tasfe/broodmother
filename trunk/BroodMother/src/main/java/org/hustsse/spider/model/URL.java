package org.hustsse.spider.model;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import org.hustsse.spider.exception.PipelineException;
import org.hustsse.spider.exception.URIException;
import org.hustsse.spider.pipeline.Pipeline;

public class URL {
	private static final int DEFAULT_PORT = 80;
	private static final String DEFAULT_ROOT_PATH = "/";

	private java.net.URL url;
	private Pipeline pipeline;
	private Map<String, Object> processorAttrs = new HashMap<String, Object>();

	/**
	 * 在创建一个URI时，必须为其指定处理它的pipeline。
	 *
	 * 在这里会将pipeline绑定到URI，不需要也不能再手动指定Pipeline所服务的URI
	 *
	 * @param url
	 * @param pipeline
	 */
	public URL(String url, Pipeline pipeline) {
		try {
			this.url = new java.net.URL(url);
			pipeline.attachTo(this);
		} catch (MalformedURLException e) {
			throw new URIException(e);
		}
	}

	public Pipeline getPipeline() {
		return pipeline;
	}

	public void setPipeline(Pipeline pipeline) {
		if (this.pipeline == null) {
			this.pipeline = pipeline;
			return;
		}
		throw new PipelineException("pipeline一旦指定就无法更改！");
	}

	public String toString() {
		return url.toString();
	}

	public void setProcessorAttr(String key, Object val) {
		this.processorAttrs.put(key, val);
	}

	public Object getProcessorAttr(String key) {
		return this.processorAttrs.get(key);
	}

	public String getHost() {
		return url.getHost();
	}

	public int getPort() {
		int port = url.getPort();
		return port < 0 ? DEFAULT_PORT : port;
	}

	public String getPath() {
		String path = url.getPath();
		return path.equals("") ? DEFAULT_ROOT_PATH : path;
	}

	public String getQuery() {
		return url.getQuery();
	}

}
