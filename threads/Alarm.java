package nachos.threads;

import java.util.*;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 *
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {

		waitlist = new PriorityQueue<WaitThread>();

		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});

	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {

		boolean intStatus = Machine.interrupt().disable();

		while (waitlist.peek()!=null && waitlist.peek().time <= Machine.timer().getTime()){
        waitlist.poll().trd.ready();
		}
		KThread.yield();
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 *
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 *
	 * @param x the minimum number of clock ticks to wait.
	 *
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		long wakeTime = Machine.timer().getTime() + x;

		WaitThread trd = new WaitThread(KThread.currentThread(),wakeTime);
		boolean intStatus = Machine.interrupt().disable();
	  waitlist.add(trd);
		KThread.sleep();
		Machine.interrupt().restore(intStatus);

	}
	private class WaitThread implements Comparable <WaitThread>{
		KThread trd;
		long time;
		public WaitThread(KThread trd, long time){
			this.trd = trd;
			this.time = time;
		}
		public int compareTo(WaitThread trd){
			if (this.time == trd.time) return 0;
			else if(this.time < trd.time) return -1;
			else return 1;
		}
	}

	private PriorityQueue<WaitThread> waitlist;
}
