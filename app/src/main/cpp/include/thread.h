//
// Created by tainzhi on 2024/11/22.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_THREAD_H
#define TCAMERA_THREAD_H

#endif //TCAMERA_THREAD_H

#include "util.h"
#include <thread>

class Thread {
private:
    std::thread _thread;
    virtual void run() = 0;
public:
    void join();
    void start();
};