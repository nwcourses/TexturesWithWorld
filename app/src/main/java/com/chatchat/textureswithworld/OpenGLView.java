package com.chatchat.textureswithworld;

/**
 * Created by whitelegg_n on 12/01/2016.
 */

import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.content.Context;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.util.Log;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;


public class OpenGLView extends GLSurfaceView implements GLSurfaceView.Renderer {


    GPUInterface textureInterface, gpu;
    FloatBuffer vbuf, tbuf;
    SurfaceTexture cameraFeedSurfaceTexture;
    CameraCapturer cameraCapturer;
    ShortBuffer ibuf;
    float[] modelview, perspective;


    public OpenGLView (Context ctx) {
        super(ctx);
        setEGLContextClientVersion(2);
        setRenderer(this);
        modelview = new float[16];
        perspective = new float[16];
        Matrix.setIdentityM(modelview, 0);
        Matrix.setIdentityM(perspective, 0);

    }


    public void onSurfaceCreated(GL10 unused,EGLConfig config)
    {
        GLES20.glClearColor(0.0f,0.0f,0.3f,0.0f);
        GLES20.glClearDepthf(1.0f);


        final String vertexShader =
                "attribute vec4 aVertex;\n" +
                        "uniform mat4 uMvMtx, uPerspMtx;\n" +

                        "void main(void)\n" +
                        "{\n"+

                       "gl_Position =  uPerspMtx * uMvMtx * aVertex;\n" +

                        "}\n",
                fragmentShader =
                        "precision mediump float;\n" +
                                "uniform vec4 uColour;\n" +
                                "void main(void)\n"+
                                "{\n"+
                                "gl_FragColor = uColour;\n" +
                                "}\n";
        gpu= new GPUInterface(vertexShader, fragmentShader);
        createShapes();

        // http://stackoverflow.com/questions/6414003/using-surfacetexture-in-android
        final int GL_TEXTURE_EXTERNAL_OES = 0x8d65;
        int[] textureId = new int[1];
        GLES20.glGenTextures(1, textureId, 0);
        if(textureId[0] != 0)
        {
            GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureId[0]);
            GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

            cameraFeedSurfaceTexture = new SurfaceTexture(textureId[0]);

            // Must negate y when calculating texcoords from vertex coords as bitmap image data assumes
            // y increases downwards
            final String texVertexShader =
                    "attribute vec4 aVertex;\n" +
                            "varying vec2 vTextureValue;\n" +
                            "void main (void)\n" +
                            "{\n" +
                            "gl_Position = aVertex;\n" +
                            "vTextureValue = vec2(0.5*(1.0 + aVertex.x), 0.5*(1.0-aVertex.y));\n" +
                            "}\n",
                    texFragmentShader =
                            "#extension GL_OES_EGL_image_external: require\n" +
                                    "precision mediump float;\n" +
                                    "varying vec2 vTextureValue;\n" +
                                    "uniform samplerExternalOES uTexture;\n" +
                                    "void main(void)\n" +
                                    "{\n" +
                                    "gl_FragColor = texture2D(uTexture,vTextureValue);\n" +
                                    "}\n";
            textureInterface = new GPUInterface(texVertexShader, texFragmentShader);
            GPUInterface.setupTexture(textureId[0]);
            textureInterface.setUniform1i("uTexture", 0); // this is the on-gpu texture register not the texture id

            cameraCapturer = new CameraCapturer();
            onResume();
        }
    }


    public void onDrawFrame(GL10 unused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Necessary to prevent texture appearing in front of the world shapes
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        if(cameraCapturer.isActive()) {
           cameraFeedSurfaceTexture.updateTexImage();
            textureInterface.select();
           textureInterface.drawIndexedBufferedData(vbuf, ibuf, 0, "aVertex");
        }

        // Re-enable depth test to show shapes correctly relative to each other
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        gpu.select();
        gpu.sendMatrix(modelview, "uMvMtx");
        gpu.sendMatrix(perspective,  "uPerspMtx");
        gpu.setUniform4fv("uColour", new float[]{1, 0, 0, 1});
        gpu.drawBufferedData(tbuf, 0, "aVertex", 0, 3);
        gpu.setUniform4fv("uColour", new float[]{1, 1, 0, 1});
        gpu.drawBufferedData(tbuf, 0, "aVertex", 3, 3);


    }

    public void onSurfaceChanged(GL10 unused, int w, int h) {

        float hFov = 50.0f;
        GLES20.glViewport(0, 0, w, h);
        float aspectRatio = (float) w / h;
        Matrix.perspectiveM(perspective, 0, hFov / aspectRatio, aspectRatio, 0.1f, 100);
    }

    private void createShapes() {

        // this seemed to crash first time in onDrawFrame(), now seems to work
        // better off going in onSurfaceCreated() anyway, more efficient

       float[] cameraRect = { -1,1,0,  -1,-1,0, 1,-1,0,  1,1,0 };

        short[] indices = { 0,1,2, 2,3,0 };

        ByteBuffer vbuf0 = ByteBuffer.allocateDirect(cameraRect.length * Float.SIZE);
        vbuf0.order(ByteOrder.nativeOrder());
        vbuf = vbuf0.asFloatBuffer();
        vbuf.put(cameraRect);
        vbuf.position(0);

        ByteBuffer ibuf0 = ByteBuffer.allocateDirect(indices.length * Short.SIZE);
        ibuf0.order(ByteOrder.nativeOrder());
        ibuf = ibuf0.asShortBuffer();
        ibuf.put(indices);
        ibuf.position(0);


        // this seemed to crash first time in onDrawFrame(), now seems to work
        // better off going in onSurfaceCreated() anyway, more efficient
        float[] vertices = { 0,0,-3f, 0.5f, 0, -3f, 0.25f, 0.5f, -3f, -0.5f,0,-6f, 0.5f, 0, -6f, 0, 0.5f, -6f};


        ByteBuffer tbuf0 = ByteBuffer.allocateDirect(vertices.length * Float.SIZE);
        tbuf0.order(ByteOrder.nativeOrder());
        tbuf = tbuf0.asFloatBuffer();
        tbuf.put(vertices);
        tbuf.position(0);

    }


    // copypasted from Hikar - removed fov stuff
    public void onResume()
    {
        if(cameraCapturer!=null)
        {

            try
            {
                cameraCapturer.openCamera();
                cameraCapturer.startPreview(cameraFeedSurfaceTexture);
            }
            catch(Exception e)
            {
                Log.e("Textures","Error getting camera preview: " + e);
                cameraCapturer.releaseCamera();
            }
        }
    }

    public void onPause() {
        cameraCapturer.releaseCamera();
    }
}
