# include "looper.h"

struct LooperMessage {
    int what;
    void *obj;
    LooperMessage *next;
    bool quit;
};

bool Looper::DEBUG =true;
Looper::Looper(): running(true), worker(&Looper::loop, this){
    LOGD("%s created", __FUNCTION__);
    head = nullptr;
}

Looper::~Looper() {
    if (running) {
        LOGV("%s, Looper deleted while still running. Some messages will not be processed", __FUNCTION__ );
        quit();
    }
}


void Looper::post(int what, void *data, bool flush) {
    if (DEBUG)
        LOGD("%s, flush:%d", __FUNCTION__, flush);
    LooperMessage *msg = new LooperMessage();
    msg->what = what;
    msg->obj = data;
    msg->next = nullptr;
    msg->quit = false;
    addMsg(msg, flush);
}

void Looper::addMsg(LooperMessage *msg, bool flush) {
    if (DEBUG)
        LOGV("%s, msg %d, dataAddress:%d, data:%d", __FUNCTION__, msg->what, reinterpret_cast<int *>(msg->obj),
             *reinterpret_cast<int *>(msg->obj));
    std::unique_lock<std::mutex> lock(looperMutex);
    LooperMessage *h = head;
    if (flush) {
        while (h) {
            LooperMessage *next = h->next;
            delete h;
            h = next;
        }
        h = nullptr;
    }

    if (h) {
        while (h->next) {
            h = h->next;
        }
        h->next = msg;
    } else {
        head = msg;
    }
    lock.unlock();
    msgQueueChangedCond.notify_one();
}

void Looper::loop() {
    do {
    } while(loopOnce());
}

bool Looper::loopOnce() {
    std::unique_lock<std::mutex> lock(looperMutex);
    LooperMessage *msg = head;
    if (msg == nullptr) {
        msgQueueChangedCond.wait(lock);
        return true;
    }
    head = msg->next;
    lock.unlock();
    
    if (msg->quit) {
        if (DEBUG)
            LOGV("%s, quitting", __FUNCTION__);
        delete msg;
        return false;
    }
    if (DEBUG)
        LOGV("%s, msg %d, dataAddress:%d, data:%d", __FUNCTION__, msg->what, reinterpret_cast<int *>(msg->obj),
             *reinterpret_cast<int *>(msg->obj));
    handle(msg->what, msg->obj);
    delete msg;
    return true;
}

void Looper::quit() {
    if (DEBUG)
        LOGV("%s quit", __FUNCTION__ );
    auto *msg = new LooperMessage();
    msg->what = 0;
    msg->obj = nullptr;
    msg->next = nullptr;
    msg->quit = true;
    addMsg(msg, false);
    if (worker.joinable()) {
        worker.join();
    }
    running = false;
}