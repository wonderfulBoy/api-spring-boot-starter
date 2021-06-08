package com.github.api.sun.tools.javac.jvm;

public interface CRTFlags {

    int CRT_STATEMENT = 0x0001;
    int CRT_BLOCK = 0x0002;
    int CRT_ASSIGNMENT = 0x0004;
    int CRT_FLOW_CONTROLLER = 0x0008;
    int CRT_FLOW_TARGET = 0x0010;
    int CRT_INVOKE = 0x0020;
    int CRT_CREATE = 0x0040;
    int CRT_BRANCH_TRUE = 0x0080;
    int CRT_BRANCH_FALSE = 0x0100;

    int CRT_VALID_FLAGS = CRT_STATEMENT | CRT_BLOCK | CRT_ASSIGNMENT |
            CRT_FLOW_CONTROLLER | CRT_FLOW_TARGET | CRT_INVOKE |
            CRT_CREATE | CRT_BRANCH_TRUE | CRT_BRANCH_FALSE;
}
