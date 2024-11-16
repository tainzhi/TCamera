# include "looper.h"

struct LooperMessage;
typedef struct LooperMessage loopermessage;

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
    std::lock_guard _l(lock);
    LooperMessage *msg = new LooperMessage();
    msg->what = what;
    msg->obj = data;
    msg->next = nullptr;
    msg->quit = false;
    addMsg(msg, flush);
}

void Looper::addMsg(LooperMessage *msg, bool flush) {
    if (DEBUG)
        LOGV("%s, msg:%d, flush:%d", __FUNCTION__ , msg->what, flush);
    std::lock_guard _l(lock);
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
        if (DEBUG)
            LOGD("%s, h is null", __FUNCTION__ );
        head = msg;
    }
}

void Looper::loop() {
    while (true) {
        lock.lock();
        LooperMessage *msg = head;
        if (msg == nullptr) {
            lock.unlock();
            continue;
        }
        if (DEBUG)
            LOGD("%s, msg isn't null", __FUNCTION__);
        head = msg->next;
        lock.unlock();

        if (msg->quit) {
            if (DEBUG)
                LOGV("%s, quitting", __FUNCTION__);
            delete msg;
            return;
        }
        if (DEBUG)
            LOGV("%s, processing msg %d", __FUNCTION__, msg->what);
        handle(msg->what, msg->obj);
        delete msg;
    }
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