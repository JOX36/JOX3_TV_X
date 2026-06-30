package com.jox3.tv.model;

import java.io.Serializable;

public class MediaItem implements Serializable {

    public static final String LIVE = "live";
    public static final String VOD = "vod";
    public static final String SERIES = "series";

    public String id;
    public String name;
    public String logoUrl;
    public String url;
    public String category;
    public String type;

    public String seriesId;
    public int season = -1;
    public int episode = -1;

    public String synopsis;

    /**
     * Id de la cuenta (PlaylistConfig.id) de la que viene este ítem.
     * null significa "la cuenta ACTIVA" (comportamiento de siempre, sin
     * cambios para todo lo que ya existía antes del buscador global).
     * Se rellena solo para ítems encontrados en cuentas ALTERNAS, así
     * DetailActivity y PlayerActivity saben a qué servidor preguntarle
     * por episodios/EPG en vez de asumir siempre la cuenta activa.
     */
    public String sourceAccountId;

    public MediaItem() { }

    public MediaItem(String id, String name, String logoUrl, String url,
                      String category, String type) {
        this.id = id;
        this.name = name;
        this.logoUrl = logoUrl;
        this.url = url;
        this.category = category;
        this.type = type;
    }

    public String favKey() {
        return type + ":" + id;
    }

    @Override
    public String toString() {
        return name;
    }
}
