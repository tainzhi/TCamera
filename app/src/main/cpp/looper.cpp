# include "looper.h"

struct LooperMessage;
typedef struct LooperMessage loopermessage;

struct LooperMessage {
    int what;
    void *obj;
    LooperMessage *next;
    bool quit;
};

Looper::Looper(): running(true), worker(&Looper::loop, this){
}

Looper::~Looper() {
    if (running) {
        LOGV("Looper deleted while still running. Some messages will not be processed");
        quit();
    }
}

void Looper::post(int what, void *data, bool flush) {
    std::lock_guard _l(lock);
    LooperMessage *msg = new LooperMessage();
    msg->what = what;
    msg->obj = data;
    msg->next = nullptr;
    msg->quit = false;
    addMsg(msg, flush);
}

void Looper::addMsg(LooperMessage *msg, bool flush) {
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
        head = msg;
    }
    LOGV("post msg %d", msg->what);
}

void Looper::loop() {
    while (true) {
        lock.lock();
        LooperMessage *msg = head;
        if (msg == nullptr) {
            LOGV("no msg");
            lock.unlock();
            continue;
        }
        head = msg->next;
        lock.unlock();

        if (msg->quit) {
            LOGV("quitting");
            delete msg;
            return;
        }
        LOGV("processing msg %d", msg->what);
        handle(msg->what, msg->obj);
        delete msg;
    }
}

void Looper::quit() {
    LOGV("quit");
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