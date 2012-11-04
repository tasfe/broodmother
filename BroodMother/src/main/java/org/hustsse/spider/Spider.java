package org.hustsse.spider;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.hustsse.spider.frontier.Frontier;
import org.hustsse.spider.model.CrawlURL;

public class Spider {

	public static final String DEFAULT_USER_AGENT = "HUSTSSE Spider";
	private Executor bossExecutor;
	private int bossNum;
	static long DEFAULT_BOSS_THREAD_TIMEOUT = 30L;

	private Frontier frontier;

	private Spider(Executor bossExecutor,int bossNum){
		this.frontier = new Frontier();
		this.bossExecutor = bossExecutor;
		this.bossNum = bossNum;
	}

	public void start(){
		for (int i = 0; i < bossNum; i++) {
			bossExecutor.execute(new BossTask(i));
		}
	}

	public static void main(String[] args) {
		Spider s = new Spider(Executors.newCachedThreadPool(), 1);
		s.start();
	}

	class BossTask implements Runnable{
		int index;

		BossTask(int index){
			this.index = index;
		}

		@Override
		public void run() {
			String oldThreadName = Thread.currentThread().getName();
			String newThreadName = "Boss线程，#"+index;
			Thread.currentThread().setName(newThreadName);
			while(true){
				CrawlURL uriToCrawl = frontier.next();
				if(uriToCrawl == null){
					return;
				}
				System.out.println("start process "+uriToCrawl);
				uriToCrawl.getPipeline().start();
				System.out.println("end process "+uriToCrawl);
			}
			//Thread.currentThread().setName(oldThreadName);
		}
	}
}
