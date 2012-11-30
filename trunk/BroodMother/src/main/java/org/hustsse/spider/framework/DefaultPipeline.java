package org.hustsse.spider.framework;

import java.util.HashMap;
import java.util.Map;

import org.hustsse.spider.exception.PipelineException;
import org.hustsse.spider.model.CrawlController;
import org.hustsse.spider.model.CrawlURL;
import org.hustsse.spider.model.URL;

public class DefaultPipeline implements Pipeline {
	/** the url that pipeline attach to*/
	private CrawlURL url;
	private PipelineSink sink = DoNothingSink.getInstance();
	private DefaultHandlerContext  head;
	private DefaultHandlerContext  tail;
	private DefaultHandlerContext  breakpoint;
	private Map<String, HandlerContext> ctxs = new HashMap<String, HandlerContext>(10);
	private Object resumeMsg;
	private boolean isPaused = false;

	@Override
	public void start() {
		if(head != null){
			try {
				head.getHandler().process(head,url);
			}catch(Throwable e) {
				sink.exceptionCaught(url,new PipelineException(e));
			}
		}
	}

	@Override
	public void resume(Object msg) {
		if(!isPaused)
			throw new PipelineException("Pipeline不在暂停状态下无法执行resume！");
		isPaused = false;
		this.resumeMsg = msg;
		Handler breakpointHandler = breakpoint.getHandler();
		try {
			breakpointHandler.process(breakpoint,url);
		}catch(RuntimeException e) {
			sink.exceptionCaught(url,new PipelineException(e));
		}
	}

	public DefaultPipeline(Handler... handlers) {
		// 空pipeline
		if(handlers == null || handlers.length == 0) {
			return;
		}
		head = new DefaultHandlerContext(handlers[0],this);
		tail = head;
		if(handlers.length > 1){
			for (int i = 1; i < handlers.length; i++) {
				DefaultHandlerContext cur = new DefaultHandlerContext(handlers[i],this);
				ctxs.put(handlers[i].getName(), cur);
				tail.setNext(cur);
				tail = cur;
			}
		}
	}

	public DefaultPipeline(PipelineSink sink,Handler... handlers) {
		this(handlers);
		append(sink);
	}


	@Override
	public CrawlURL getURL() {
		return url;
	}

	@Override
	public PipelineSink getSink() {
		return sink;
	}

	@Override
	public void append(PipelineSink sink) {
		if(sink == null)
			this.sink = DoNothingSink.getInstance();
		else
			this.sink = sink;
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
		public void uriSunk(CrawlURL e)  {
		}

		@Override
		public void exceptionCaught(CrawlURL e, PipelineException cause) {
		}
	}


	@Override
	public boolean isPaused() {
		return isPaused;
	}

	@Override
	public void attachTo(CrawlURL url) {
		this.url = url;
		url.setPipeline(this);
	}

	@Override
	public Object getMessage() {
		return resumeMsg;
	}

	@Override
	public void clearMessage() {
		resumeMsg = null;
	}

	@Override
	public HandlerContext getHandlerContext(String handlerName) {
		return ctxs.get(handlerName);
	}
}
