package com.dan.thunder.data;

import com.dan.thunder.model.ThunderData;

/** Quelle für Stufen, Szene und Farbstimmungen. */
public interface ThunderRepository {
    ThunderData load(String sceneCode) throws Exception;
}
