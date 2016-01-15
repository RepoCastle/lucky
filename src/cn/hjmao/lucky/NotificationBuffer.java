package cn.hjmao.lucky;

import android.app.Notification;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by hjmao on 1/15/16.
 */
public class NotificationBuffer {

  final Lock lock = new ReentrantLock();
  final Condition notFull  = lock.newCondition();

  final Notification[] items = new Notification[500];
  int putptr, takeptr, count;

  public void put(Notification x) throws InterruptedException {
    lock.lock();
    try {
      while (count == items.length) {
        notFull.await();
      }
      items[putptr] = x;
      if (++putptr == items.length) {
        putptr = 0;
      }
      ++count;
    } finally {
      lock.unlock();
    }
  }

  public Notification take() {
    lock.lock();
    Notification x = null;
    try {
      if (count > 0) {
        x = items[takeptr];
        if (++takeptr == items.length) {
          takeptr = 0;
        }
        --count;
        notFull.signal();
      }
    } finally {
      lock.unlock();
      return x;
    }
  }
}
