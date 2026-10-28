package com.dan.thunder.gl;

import com.dan.thunder.scene.Gelaende;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Das Gelände auf der Grafikkarte (Phase 18a): ein festes Netz aus {@link Gelaende#netz()}, gezeichnet mit gelaende.vert/frag. */
final class GelaendeGl {
    private int vao, vbo, ebo, count;
    boolean ok;
    String info = "";

    void init(GL4 gl) {
        Gelaende.Netzdaten d = Gelaende.netz();
        int[] b = new int[2];
        gl.glGenVertexArrays(1, b, 0);
        vao = b[0];
        gl.glGenBuffers(2, b, 0);
        vbo = b[0];
        ebo = b[1];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(d.ecken());
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) d.ecken().length * 4, fb, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        int st = Gelaende.STRIDE * 4;
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, st, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, st, 12);
        gl.glEnableVertexAttribArray(2);
        gl.glVertexAttribPointer(2, 4, GL.GL_FLOAT, false, st, 24);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ebo);
        IntBuffer ib = Buffers.newDirectIntBuffer(d.indizes());
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) d.indizes().length * 4, ib, GL.GL_STATIC_DRAW);
        gl.glBindVertexArray(0);
        count = d.indizes().length;
        ok = true;
        info = String.format(java.util.Locale.GERMAN, "%,d Dreiecke", count / 3);
    }

    void draw(GL4 gl) {
        if (!ok) return;
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(vao);
        gl.glDrawElements(GL.GL_TRIANGLES, count, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
    }

    void dispose(GL4 gl) {
        if (!ok) return;
        gl.glDeleteBuffers(2, new int[]{vbo, ebo}, 0);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        ok = false;
    }
}
