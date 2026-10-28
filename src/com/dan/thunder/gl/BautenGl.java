package com.dan.thunder.gl;

import com.dan.thunder.scene.Netz;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.IntBuffer;

/**
 * Türme und Bauten auf der Grafikkarte (seit Phase 10): je Netz ein Puffer und ein VAO (Ort 3 × float, Normale 4 × Byte,
 * Merkmal 1 × uint, 20 Byte je Ecke), gezeichnet mit {@code bauten.vert/frag} nach dem Gelände, mit Tiefentest, ohne
 * Rückseitenauslese (die Normale wendet der Shader zum Auge). Drei Netze: fest (Häuser, Türme, Wege), Strandkörbe (neu bei
 * Änderung) und Herde (jedes Bild); seit Phase 12 die Brocken eines Abbruchs am Kliff (jedes Bild, solange er fällt).
 */
final class BautenGl {
    static final int FEST = 0, KOERBE = 1, HERDE = 2, BROCKEN = 3, SPUR = 4, N = 5;
    private final int[] vao = new int[N], vbo = new int[N], ecken = new int[N], platz = new int[N];
    boolean ok;
    String info = "";

    void init(GL4 gl) {
        gl.glGenVertexArrays(N, vao, 0);
        gl.glGenBuffers(N, vbo, 0);
        for (int i = 0; i < N; i++) {
            gl.glBindVertexArray(vao[i]);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[i]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, 20, null, i == FEST ? GL.GL_STATIC_DRAW : GL.GL_DYNAMIC_DRAW);
            gl.glEnableVertexAttribArray(0);
            gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 20, 0);
            gl.glEnableVertexAttribArray(1);
            gl.glVertexAttribPointer(1, 4, GL.GL_BYTE, true, 20, 12);
            gl.glEnableVertexAttribArray(2);
            gl.glVertexAttribIPointer(2, 1, GL.GL_UNSIGNED_INT, 20, 16);
        }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        ok = true;
    }

    /** Ein Netz in den Puffer i laden (dynamische Puffer wachsen nur). */
    void upload(GL4 gl, int i, Netz n) {
        if (!ok) return;
        IntBuffer b = Buffers.newDirectIntBuffer(Math.max(Netz.STRIDE, n.laenge()));
        b.put(n.daten(), 0, n.laenge());
        b.rewind();
        long bytes = (long) n.laenge() * 4;
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[i]);
        if (i == FEST || bytes > platz[i]) {
            long cap = i == FEST ? bytes : Math.max(bytes, platz[i] * 2L);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, Math.max(20, cap), null, i == FEST ? GL.GL_STATIC_DRAW : GL.GL_DYNAMIC_DRAW);
            platz[i] = (int) Math.max(20, cap);
        }
        if (bytes > 0) gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, bytes, b);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        ecken[i] = n.ecken();
        if (i == FEST) info = String.format(java.util.Locale.GERMAN, "%,d Dreiecke, %.0f MB", n.dreiecke(), bytes / 1e6);
    }

    /** Zeichnen (das Programm ist schon benutzt und hat seine Uniforms). */
    void draw(GL4 gl, boolean fest, boolean koerbe, boolean herde) { draw(gl, fest, koerbe, herde, true); }

    void draw(GL4 gl, boolean fest, boolean koerbe, boolean herde, boolean brocken) {
        if (!ok) return;
        gl.glDisable(GL.GL_CULL_FACE);
        boolean[] an = {fest, koerbe, herde, brocken, true};
        for (int i = 0; i < N; i++) {
            if (!an[i] || ecken[i] == 0) continue;
            gl.glBindVertexArray(vao[i]);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, ecken[i]);
        }
        gl.glBindVertexArray(0);
    }

    int dreiecke(int i) { return ecken[i] / 3; }

    void dispose(GL4 gl) {
        if (!ok) return;
        gl.glDeleteBuffers(N, vbo, 0);
        gl.glDeleteVertexArrays(N, vao, 0);
        ok = false;
    }
}
