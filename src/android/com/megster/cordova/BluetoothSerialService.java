package com.megster.cordova;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.Arrays;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import android.widget.Toast;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 *
 * This code was based on the Android SDK BluetoothChat Sample
 * $ANDROID_SDK/samples/android-17/BluetoothChat
 */
public class BluetoothSerialService {

    // Debugging
    private static final String TAG = "BluetoothSerialService";
    private static final boolean D = true;

    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "PhoneGapBluetoothSerialServiceSecure";
    private static final String NAME_INSECURE = "PhoneGapBluetoothSerialServiceInSecure";

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE = UUID.fromString("7A9C3B55-78D0-44A7-A94E-A93E3FE118CE");
    private static final UUID MY_UUID_INSECURE = UUID.fromString("23F18142-B389-4772-93BD-52BDBB2C03E9");

    // Well known SPP UUID
    private static final UUID UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    /**
     * Constructor. Prepares a new BluetoothSerial session.
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BluetoothSerialService(Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(BluetoothSerial.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        setState(STATE_NONE);

//      Listen isn't working with Arduino. Ignore since assuming the phone will initiate the connection.
//        setState(STATE_LISTEN);
//
//        // Start the thread to listen on a BluetoothServerSocket
//        if (mSecureAcceptThread == null) {
//            mSecureAcceptThread = new AcceptThread(true);
//            mSecureAcceptThread.start();
//        }
//        if (mInsecureAcceptThread == null) {
//            mInsecureAcceptThread = new AcceptThread(false);
//            mInsecureAcceptThread.start();
//        }
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device, final String socketType) {
        if (D) Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(BluetoothSerial.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothSerial.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }
        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     * byte[]
     */
    public void write(String out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }
    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void image(String out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothSerial.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothSerial.TOAST, "Unable to connect to device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothSerialService.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothSerial.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothSerial.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothSerialService.this.start();
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure":"Insecure";

            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, MY_UUID_SECURE);
                } else {
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (D) Log.d(TAG, "Socket Type: " + mSocketType + "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket;

            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothSerialService.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.getRemoteDevice(),
                                        mSocketType);
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

        }

        public void cancel() {
            if (D) Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
            }
        }
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private /*final*/ BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Get a BluetoothSocket for a connection with the given BluetoothDevice
            try {
                if (secure) {
                    // tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
                    tmp = device.createRfcommSocketToServiceRecord(UUID_SPP);
                } else {
                    //tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
                    tmp = device.createInsecureRfcommSocketToServiceRecord(UUID_SPP);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a successful connection or an exception
                Log.i(TAG,"Connecting to socket...");
                mmSocket.connect();
                Log.i(TAG,"Connected");
            } catch (IOException e) {
                Log.e(TAG, e.toString());

                // Some 4.1 devices have problems, try an alternative way to connect
                // See https://github.com/don/BluetoothSerial/issues/89
                try {
                    Log.i(TAG,"Trying fallback...");
                    mmSocket = (BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(mmDevice,1);
                    mmSocket.connect();
                    Log.i(TAG,"Connected");
                } catch (Exception e2) {
                    Log.e(TAG, "Couldn't establish a Bluetooth connection.");
                    try {
                        mmSocket.close();
                    } catch (IOException e3) {
                        Log.e(TAG, "unable to close() " + mSocketType + " socket during connection failure", e3);
                    }
                    connectionFailed();
                    return;
                }
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothSerialService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    String data = new String(buffer, 0, bytes);

                    // Send the new data String to the UI Activity
                    mHandler.obtainMessage(BluetoothSerial.MESSAGE_READ, data).sendToTarget();

                    // Send the raw bytestream to the UI Activity.
                    // We make a copy because the full array can have extra data at the end
                    // when / if we read less than its size.
                    if (bytes > 0) {
                        byte[] rawdata = Arrays.copyOf(buffer, bytes);
                        mHandler.obtainMessage(BluetoothSerial.MESSAGE_READ_RAW, rawdata).sendToTarget();
                    }

                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    // Start the service over to restart listening mode
                    BluetoothSerialService.this.start();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void image(String buffer) {
            try {

                String  sig = buffer;
            byte[] imageAsBytes = android.util.Base64.decode(sig, android.util.Base64.DEFAULT);


            Bitmap btMap = BitmapFactory.decodeByteArray(imageAsBytes, 0,
                    imageAsBytes.length);

             char ESC_CHAR = 0x1B;
             char GS = 0x1D;
             byte[] LINE_FEED = new byte[]{0x0A};
             byte[] CUT_PAPER = new byte[]{0x1D, 0x56, 0x00};
             byte[] INIT_PRINTER = new byte[]{0x1B, 0x40};
             byte[] SELECT_BIT_IMAGE_MODE = {0x1B, 0x2A, 33};
             byte[] SET_LINE_SPACE_24 = new byte[]{0x1B, 0x33, 24};
            //  mmOutStream.write(buffer);
            // Imake same = new Imake();
             int[][] pixels = same.getPixelsSlow(btMap);
             mmOutStream.write(SET_LINE_SPACE_24);
            for (int y = 0; y < pixels.length; y += 24) {
            // Like I said before, when done sending data,
            // the printer will resume to normal text printing
            mmOutStream.write(SELECT_BIT_IMAGE_MODE);
            // Set nL and nH based on the width of the image
            mmOutStream.write(new byte[]{(byte)(0x00ff & pixels[y].length)
                                        , (byte)((0xff00 & pixels[y].length) >> 8)});
            for (int x = 0; x < pixels[y].length; x++) {
            // for each stripe, recollect 3 bytes (3 bytes = 24 bits)
            mmOutStream.write(same.recollectSlice(y, x, pixels));
            }

            // Do a line feed, if not the printing will resume on the same line
            mmOutStream.write(LINE_FEED);
            }
            // mmOutStream.write(SET_LINE_SPACE_30);

            // Bitmap bitmapOrg = same.resizeImage(btMap, 384,
            //         150);// Bit
            // byte[] sendbuf = same.StartBmpToPrintCode(bitmapOrg, 0);

            //     mmOutStream.write(sendbuf);
            //     mmOutStream.flush();

           // mmOutStream.write(LINE_FEED);
           // mmOutStream.write(LINE_FEED);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(BluetoothSerial.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();

            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

             /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(BluetoothSerial.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();

            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }
        

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    public class Imake {

        public byte[] recollectSlice(int y, int x, int[][] img) {
                byte[] slices = new byte[] {0, 0, 0};
                for (int yy = y, i = 0; yy < y + 24 && i < 3; yy += 8, i++) {
                    byte slice = 0;
            for (int b = 0; b < 8; b++) {
                        int yyy = yy + b;
                if (yyy >= img.length) {
                    continue;
                }
                int col = img[yyy][x];
                boolean v = shouldPrintColor(col);
                slice |= (byte) ((v ? 1 : 0) << (7 - b));
            }
                    slices[i] = slice;
                }

                return slices;
            }

            public boolean shouldPrintColor(int col) {
                final int threshold = 127;
                int a, r, g, b, luminance;
                a = (col >> 24) & 0xff;
                if (a != 0xff) {// Ignore transparencies
                    return false;
                }
                r = (col >> 16) & 0xff;
                g = (col >> 8) & 0xff;
                b = col & 0xff;

                luminance = (int) (0.299 * r + 0.587 * g + 0.114 * b);

                return luminance < threshold;
            }

        public int[][] getPixelsSlow(Bitmap image) {
                int width = image.getWidth();
                int height = image.getHeight();
                int[][] result = new int[height][width];
                for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                result[row][col] = getRGB(image, col, row);
                }
            }

            return result;
        }

            public int getRGB(Bitmap bmpOriginal, int col, int row) {
                    // get one pixel color
                    int pixel = bmpOriginal.getPixel(col, row);
                    // retrieve color of all channels
                    int R = Color.red(pixel);
                    int G = Color.green(pixel);
                    int B = Color.blue(pixel);
                    return Color.rgb(R, G, B);
            }



        //  public  Bitmap resizeImage(Bitmap bitmap, int w, int h) {
        //     Bitmap BitmapOrg = bitmap;
        //     int width = BitmapOrg.getWidth();
        //     int height = BitmapOrg.getHeight();
        //     int newWidth = w;
        //     int newHeight = h;

        //     float scaleWidth = ((float) newWidth) / width;
        //     float scaleHeight = ((float) newHeight) / height;
        //     Matrix matrix = new Matrix();
        //     matrix.postScale(scaleWidth, scaleWidth);
        //     Bitmap resizedBitmap = Bitmap.createBitmap(BitmapOrg, 10,10, width,
        //             height, matrix, true);
        //     return resizedBitmap;
        // }
// public  byte[] StartBmpToPrintCode(Bitmap bitmap, int t) {
//     byte temp = 0;
//     int j = 7;
//     int start = 0;
//     if (bitmap != null) {
//         int mWidth = bitmap.getWidth();
//         int mHeight = bitmap.getHeight();

//         int[] mIntArray = new int[mWidth * mHeight];
//         byte[] data = new byte[mWidth * mHeight];
//         bitmap.getPixels(mIntArray, 0, mWidth, 0, 0, mWidth, mHeight);
//         Imake mama  = new Imake();
//         mama.encodeYUV420SP(data, mIntArray, mWidth, mHeight, t);
//         byte[] result = new byte[mWidth * mHeight / 8];
//         for (int i = 0; i < mWidth * mHeight; i++) {
//             temp = (byte) ((byte) (data[i] << j) + temp);
//             j--;
//             if (j < 0) {
//                 j = 7;
//             }
//             if (i % 8 == 7) {
//                 result[start++] = temp;
//                 temp = 0;
//             }
//         }
//         if (j != 7) {
//             result[start++] = temp;
//         }

//         int aHeight = 24 - mHeight % 24;
//         byte[] add = new byte[aHeight * 48];
//         byte[] nresult = new byte[mWidth * mHeight / 8 + aHeight * 48];
//         System.arraycopy(result, 0, nresult, 0, result.length);
//         System.arraycopy(add, 0, nresult, result.length, add.length);

//         byte[] byteContent = new byte[(mWidth / 8 + 4)
//                 * (mHeight + aHeight)];// ´òÓ¡Êý×é
//         byte[] bytehead = new byte[4];// Ã¿ÐÐ´òÓ¡Í·
//         bytehead[0] = (byte) 0x1f;
//         bytehead[1] = (byte) 0x10;
//         bytehead[2] = (byte) (mWidth / 8);
//         bytehead[3] = (byte) 0x00;
//         for (int index = 0; index < mHeight + aHeight; index++) {
//             System.arraycopy(bytehead, 0, byteContent, index * 52, 4);
//             System.arraycopy(nresult, index * 48, byteContent,
//                     index * 52 + 4, 48);

//         }
//         return byteContent;
//     }
//     return null;

// }

// public  void encodeYUV420SP(byte[] yuv420sp, int[] rgba, int width,
//         int height, int t) {
//     final int frameSize = width * height;
//     int[] U, V;
//     U = new int[frameSize];
//     V = new int[frameSize];
//     final int uvwidth = width / 2;
//     int r, g, b, y, u, v;
//     int bits = 8;
//     int index = 0;
//     int f = 0;
//     for (int j = 0; j < height; j++) {
//         for (int i = 0; i < width; i++) {
//             r = (rgba[index] & 0xff000000) >> 24;
//             g = (rgba[index] & 0xff0000) >> 16;
//             b = (rgba[index] & 0xff00) >> 8;
//             // rgb to yuv
//             y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
//             u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
//             v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
//             // clip y
//             // yuv420sp[index++] = (byte) ((y < 0) ? 0 : ((y > 255) ? 255 :
//             // y));
//             byte temp = (byte) ((y < 0) ? 0 : ((y > 255) ? 255 : y));
//             if (t == 0) {
//                 yuv420sp[index++] = temp > 0 ? (byte) 1 : (byte) 0;
//             } else {
//                 yuv420sp[index++] = temp > 0 ? (byte) 0 : (byte) 1;
//             }

//             // {
//             // if (f == 0) {
//             // yuv420sp[index++] = 0;
//             // f = 1;
//             // } else {
//             // yuv420sp[index++] = 1;
//             // f = 0;
//             // }

//             // }

//         }

//     }
//     f = 0;
// }
    }
}
