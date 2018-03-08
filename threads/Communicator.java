package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
        //flag = false;
        lock=new Lock();
        listener=new Condition2(lock);
        speaker=new Condition2(lock);
        waitingQueue = new Condition2(lock);
        word = null;
    }
    
    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param word the integer to transfer.
     */
    public void speak(int word) {
        lock.acquire();
        //speakercnt++;
        while( this.word != null) {
            speaker.sleep();
        }
        this.word = word;
        //flag = true;
        listener.wake();
        
        waitingQueue.sleep();
        lock.release();
    }
    
    /**
     * Wait for a thread to speak through this communicator, and then return the
     * <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return the integer transferred.
     */
    public int listen() {
        lock.acquire();
        //listenercnt++;
        while(word == null) {
            //speaker.wakeAll();
            listener.sleep();
        }
        int data = word.intValue();
        word = null;
        //listenercnt--;
        waitingQueue.wake();
        speaker.wake();
        lock.release();
        return data;
    }
    //private boolean flag;
    //private int listenercnt = 0;
    //private int speakercnt = 0;
    private Condition2 speaker;
    private Condition2 listener;
    private Condition2 waitingQueue;
    private Lock lock;
    private Integer word;
}

