package org.hustsse.spider;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.hustsse.spider.framework.Frontier;
import org.hustsse.spider.model.CrawlURL;

public class Spider {

	public static final String DEFAULT_USER_AGENT = "HUSTSSE Spider";
	private Executor bossExecutor;
	private int bossNum;

	private Frontier frontier;

	private Spider(Executor bossExecutor,int bossNum){
		this.frontier = new Frontier();
		this.bossExecutor = bossExecutor;
		this.bossNum = bossNum;
	}

	public void start(){
		for (int i = 0; i < bossNum; i++) {
			bossExecutor.execute(new CrawlTask(i));
		}
	}

	public static void main(String[] args) {
		Spider s = new Spider(Executors.newCachedThreadPool(), 1);
		s.start();
	}

	class CrawlTask implements Runnable{
		int index;

		CrawlTask(int index){
			this.index = index;
		}

		@Override
		public void run() {
			String newThreadName = "Boss线程，#"+index;
			Thread.currentThread().setName(newThreadName);
			while(true){
				CrawlURL uriToCrawl = frontier.next();
				if(uriToCrawl == null){
					return;
				}
				uriToCrawl.getPipeline().start();
			}
		}
	}
}
