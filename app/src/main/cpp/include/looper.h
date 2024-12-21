/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


#ifndef TCAMERA_LOOPER_H
#define TCAMERA_LOOPER_H

#include "util.h"
#include "thread.h"

struct LooperMessage;

class Looper: public Thread {
public:
    Looper();

    Looper &operator=(const Looper &) = delete;

    Looper(Looper &) = delete;
    
    void run() override;

    virtual ~Looper();

    void post(int what, void *data, bool flush = false);

    void quit();

    virtual void handle(int what, void *data) = 0;

private:
    static bool DEBUG;
    void addMsg(LooperMessage *msg, bool flush);

    void loop();
    
    bool loopOnce();

    LooperMessage *head;
    std::mutex looperMutex;
    std::condition_variable msgQueueChangedCond;
    bool running;
};

#endif //TCAMERA_LOOPER_H