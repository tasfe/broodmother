package org.hustsse.spider.handler;

import org.hustsse.spider.exception.PipelineException;
import org.hustsse.spider.pipeline.DefaultPipeline;

public class DefaultHandlerContext implements HandlerContext {

	private Object attachment;
	private DefaultPipeline pipeline;
	private DefaultHandlerContext next;
	private Handler handler;

	public DefaultHandlerContext(Handler handler, DefaultPipeline pipeline) {
		this.handler = handler;
		this.pipeline = pipeline;
	}

	@Override
	public void proceed() {
		if (next != null)
			next.getHandler().process(next, pipeline.getURI());
		else {
			toSink();
		}

	}

	@Override
	public void pause() {
		if (pipeline.isPaused())
			throw new PipelineException("Pipeline不能被重复暂停！");
		pipeline.setBreakpoint(this);
	}

	@Override
	public void toSink() {
		pipeline.getSink().uriSunk(pipeline.getURI());
	}

	@Override
	public Object getAttachment() {
		return attachment;
	}

	@Override
	public void setAttachment(Object attachment) {
		this.attachment = attachment;
	}

	@Override
	public Handler getHandler() {
		return this.handler;
	}

	public DefaultHandlerContext getNext() {
		return next;
	}

	public void setNext(DefaultHandlerContext next) {
		this.next = next;
	}

}
