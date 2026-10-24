// SPDX-License-Identifier: Apache-2.0
// cgroup.kill acceptance is not proof that descendants released hardware.
#ifndef CGROUP_DRAIN160_H
#define CGROUP_DRAIN160_H
#include <stddef.h>

static inline int cgroup_populated160(const char *text,size_t length) {
    if (!text || !length || length>4096 || text[length-1]!='\n') return -1;
    int populated=-1;
    for (size_t begin=0;begin<length;) {
        size_t end=begin;
        while (end<length && text[end]!='\n') {
            if (!text[end]) return -1;
            end++;
        }
        if (end==begin) return -1;
        const char key[]="populated";
        size_t i=0;
        while (i<sizeof(key)-1 && begin+i<end && text[begin+i]==key[i]) i++;
        if (i==sizeof(key)-1 && (begin+i==end || text[begin+i]==' ' || text[begin+i]=='\t')) {
            if (populated!=-1 || begin+i==end) return -1;
            size_t value=begin+i;
            while (value<end && (text[value]==' ' || text[value]=='\t')) value++;
            if (value==end || (text[value]!='0' && text[value]!='1')) return -1;
            populated=text[value++]-'0';
            while (value<end && (text[value]==' ' || text[value]=='\t')) value++;
            if (value!=end) return -1;
        }
        begin=end+1;
    }
    return populated;
}

struct cgroup_drain_ops160 {
    int (*sample)(void *); // -1 unreadable/invalid, 0 drained, 1 populated
    int (*pause)(void *);  // wait and maintain watchdog; nonzero fails
    void *context;
};

static inline int cgroup_drain_wait160(const struct cgroup_drain_ops160 *ops,unsigned attempts) {
    if (!ops || !ops->sample || !ops->pause || !attempts || attempts>1000) return -1;
    for (unsigned i=0;i<attempts;i++) {
        int state=ops->sample(ops->context);
        if (state==0) return 0;
        if (state!=1) return -1;
        if (i+1<attempts && ops->pause(ops->context)) return -1;
    }
    return -1;
}
#endif
