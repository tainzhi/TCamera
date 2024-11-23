//
// Created by tainzhi on 2024/11/22.
// Email: qfq61@qq.com
//

#include "thread.h"

void Thread::start() {
    _thread = std::thread([this] { this->run(); });
}

void Thread::join() {
    _thread.join();
}
