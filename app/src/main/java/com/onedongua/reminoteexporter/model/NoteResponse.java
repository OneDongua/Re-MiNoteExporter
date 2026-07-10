package com.onedongua.reminoteexporter.model;

import java.util.List;

public class NoteResponse {
    public String result;
    public boolean retriable;
    public int code;
    public Data data;

    public static class Data {
        public List<NoteEntry> entries;
        public List<NoteFolder> folders;
        public String syncTag;
    }
}
