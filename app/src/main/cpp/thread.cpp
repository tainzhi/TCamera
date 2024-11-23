//
// Created by tainzhi on 2024/11/22.
// Email: qfq61@qq.com
//

#include "thread.h"

void Thread::start() {
    _thread = std::thread([this] {
        this->started = true;
        this->run();
        this->started = false;
    });
}

bool Thread::isRunning() {
    return started;
}

void Thread::stop() {

}

void Thread::join() {
    if (isRunning) {
        _thread.join();
    }
}
