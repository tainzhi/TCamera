//
// Created by tainzhi on 2024/11/22.
// Email: qfq61@qq.com
//

#include "thread.h"

void Thread::start() {
    _thread = std::thread([this] {
        this->isRunning = true;
        this->run();
        this->isRunning = false;
    });
}

void Thread::stop() {

}

void Thread::join() {
    if (isRunning) {
        _thread.join();
    }
}
