package com.onedongua.reminoteexporter.model;

import com.google.gson.annotations.SerializedName;

public class ExtraInfo {
    @SerializedName("note_content_type")
    public String noteContentType;
    @SerializedName("web_images")
    public String webImages;
    @SerializedName("mind_content_plain_text")
    public String mindContentPlainText;
    public String title;
    @SerializedName("mind_content")
    public String mindContent;
}
