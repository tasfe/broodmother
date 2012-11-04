package org.hustsse.spider.pipeline;

import org.hustsse.spider.exception.PipelineException;
import org.hustsse.spider.handler.DefaultHandlerContext;
import org.hustsse.spider.handler.Handler;
import org.hustsse.spider.model.URL;

public class DefaultPipeline implements Pipeline {

	private URL uri;
	private PipelineSink sink = DoNothingSink.getInstance();
	private DefaultHandlerContext  head;
	private DefaultHandlerContext  tail;
	private DefaultHandlerContext  breakpoint;
	private Object resumeMsg;
	private boolean isPaused = false;

	@Override
	public void start() {
		if(head != null){
			head.getHandler().process(head,uri);
		}
	}

	@Override
	public void resume(Object msg) {
		if(!isPaused)
			throw new PipelineException("Pipeline不在暂停状态下无法执行resume！");
		isPaused = false;
		this.resumeMsg = msg;
		Handler breakpointHandler = breakpoint.getHandler();
		breakpointHandler.process(breakpoint,uri);
	}

	public DefaultPipeline(Handler... handlers) {
		head = new DefaultHandlerContext(handlers[0],this);
		tail = head;
		if(handlers.length > 1){
			for (int i = 1; i < handlers.length; i++) {
				DefaultHandlerContext cur = new DefaultHandlerContext(handlers[i],this);
				tail.setNext(cur);
				tail = cur;
			}
		}
	}


	@Override
	public URL getURI() {
		return uri;
	}

	@Override
	public PipelineSink getSink() {
		return sink;
	}

	@Override
	public void bind(PipelineSink sink) {
		if(this.sink != null && this.sink != DoNothingSink.getInstance())
			this.sink = sink;
		else
			throw new PipelineException("一但 attach 了 Pipeline sink 便不能更改！");
	}

	public DefaultHandlerContext getBreakpoint() {
		return breakpoint;
	}

	public void setBreakpoint(DefaultHandlerContext breakpoint) {
		isPaused = true;
		this.breakpoint = breakpoint;
	}


	static class DoNothingSink implements PipelineSink {
		public static DoNothingSink singleton = new DoNothingSink();

		public static DoNothingSink getInstance() {
			return singleton;
		}

		private DoNothingSink() {
		}

		@Override
		public void uriSunk(URL e)  {
		}

		@Override
		public void exceptionCaught(URL e, PipelineException cause) {
		}
	}


	@Override
	public boolean isPaused() {
		return isPaused;
	}

	@Override
	public void attachTo(URL uri) {
		if(this.uri !=null || uri.getPipeline() != null)
			throw new PipelineException("pipeline一旦绑定到URI上便不能改变！");
		this.uri = uri;
		uri.setPipeline(this);
	}

	@Override
	public Object getMessage() {
		return resumeMsg;
	}

	@Override
	public void clearMessage() {
		resumeMsg = null;
	}


}
