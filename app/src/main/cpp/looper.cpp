# include "looper.h"

#define TAG "NativeLooper"

struct LooperMessage {
    int what;
    void *obj;
    LooperMessage *next;
    bool quit;
};

bool Looper::DEBUG = true;
Looper::Looper(): running(true) {
    LOGD("created");
    head = nullptr;
}

Looper::~Looper() {
    if (running) {
        LOGV("Looper deleted while still running. Some messages will not be processed");
        quit();
    }
}

void Looper::run() {
    loop();
}


void Looper::post(int what, void *data, bool flush) {
    // 不能加lock，否则会导致无法 addMsg 到队列中
    if (DEBUG)
        LOGD("flush:%d", flush);
    LooperMessage *msg = new LooperMessage();
    msg->what = what;
    msg->obj = data;
    msg->next = nullptr;
    msg->quit = false;
    addMsg(msg, flush);
}

void Looper::addMsg(LooperMessage *msg, bool flush) {
    if (DEBUG)
        LOGV("msg %d, data:%d", msg->what, *reinterpret_cast<int *>(msg->obj));
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
            LOGV("quitting");
        delete msg;
        return false;
    }
    if (DEBUG)
        LOGV("msg %d, data:%d", msg->what, *reinterpret_cast<int *>(msg->obj));
    handle(msg->what, msg->obj);
    delete msg;
    return true;
}

void Looper::quit() {
    if (DEBUG)
        LOGD();
    auto *msg = new LooperMessage();
    msg->what = 0;
    msg->obj = nullptr;
    msg->next = nullptr;
    msg->quit = true;
    addMsg(msg, false);
    join();
    running = false;
}