//
// Created by tainzhi on 2024/11/22.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_THREAD_H
#define TCAMERA_THREAD_H

#include "util.h"
#include <thread>

class Thread {
private:
    std::thread _thread;
    virtual void run() = 0;
    bool started = false;
public:
    void join();
    void start();
    void stop();
    bool isRunning();
};

template <class T>
class ThreadHolder {
private:
    std::shared_ptr<T> thread;
    std::mutex mutex;
    
public:
    ThreadHolder() {
        thread = nullptr;
    }
    std::shared_ptr<T> get() {
        std::lock_guard<std::mutex> lock(mutex);
        if (thread == nullptr) {
            thread = std::make_shared<T>();
        }
        // if (!(thread->isRunning())) {
        //     thread->start();
        // }
        thread->start();
        return thread;
    }
};

template <typename T>
std::shared_ptr<T> getThread(ThreadHolder<T> &threadHolder) {
    return threadHolder.get();
};

#endif //TCAMERA_THREAD_H
