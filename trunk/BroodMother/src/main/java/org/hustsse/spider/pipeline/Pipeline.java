package org.hustsse.spider.pipeline;

import org.hustsse.spider.model.URL;

public interface Pipeline {

	void start();

	/**
	 *
	 * @param resumeMsg 向breakpoint传递的消息，保存在pipeline中
	 */
	void resume(Object resumeMsg);

	boolean isPaused();

	URL getURI();

	PipelineSink getSink();

	void bind(PipelineSink sink);

	/**
	 *
	 * @param uri
	 */
	void attachTo(URL uri);

	Object getMessage();

	void clearMessage();
}
