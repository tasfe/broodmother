package org.hustsse.spider.framework;

import org.hustsse.spider.exception.PipelineException;
import org.hustsse.spider.model.CrawlURL;

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
		if (next != null) {
			try {
				next.getHandler().process(next, pipeline.getURL());
			}catch(RuntimeException e) {
				toSink(pipeline.getURL(),new PipelineException(e));
			}
		}
		else {
			finish();
		}

	}

	/**
	 * if exceptioned in any handler, jump to the sink.
	 * @param url
	 * @param cause
	 */
	private void toSink(CrawlURL url, PipelineException cause) {
		pipeline.getSink().exceptionCaught(url, cause);
	}

	@Override
	public void pause() {
		if (pipeline.isPaused())
			throw new PipelineException("Pipeline不能被重复暂停！");
		pipeline.setBreakpoint(this);
	}

	@Override
	public void finish() {
		pipeline.getSink().uriSunk(pipeline.getURL());
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

	@Override
	public void jumpTo(String handlerName) {
		if(handlerName == null)
			throw new NullPointerException("必须指定handler的名称！");
		HandlerContext ctx = pipeline.getHandlerContext(handlerName);
		if(ctx == null)
			throw new PipelineException("名为"+handlerName+"的handler不存在！");
		try {
			ctx.getHandler().process(ctx, pipeline.getURL());
		}catch(RuntimeException e) {
			toSink(pipeline.getURL(),new PipelineException(e));
		}
	}
}
