package com.example.sslproxy;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class MainActivity extends AppCompatActivity {
    String tunnelHost;
    int tunnelPort;
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EditText ipAddressInput = (EditText)findViewById(R.id.ipaddress);
        EditText portInput = (EditText)findViewById(R.id.port);
        Button connectButton = (Button)findViewById(R.id.connect);
        connectButton.setOnClickListener((event)->{
            String host = ipAddressInput.getText().toString();
            int port = Integer.parseInt(portInput.getText().toString());
            try {
                SSLSocketClientWithTunneling(host,port);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void SSLSocketClientWithTunneling(String host, int port) throws IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                SSLSocketFactory factory = (SSLSocketFactory)SSLSocketFactory.getDefault();
                tunnelHost = System.getProperty("https.proxyHost");
                tunnelPort = Integer.getInteger("https.proxyPort").intValue();
                Socket tunnel;
                SSLSocket socket;
                try {
                    tunnel = new Socket(tunnelHost, tunnelPort);
                    doTunnelHandshake(tunnel, host, port);
                    socket = (SSLSocket)factory.createSocket(tunnel, host, port, true);
                    socket.addHandshakeCompletedListener(
                            new HandshakeCompletedListener() {
                                public void handshakeCompleted(HandshakeCompletedEvent event) {
                                    System.out.println("Handshake finished!");
                                    System.out.println(
                                            "\t CipherSuite:" + event.getCipherSuite());
                                    System.out.println(
                                            "\t SessionId " + event.getSession());
                                    System.out.println(
                                            "\t PeerHost " + event.getSession().getPeerHost());
                                }
                            }
                    );
                    /*
                     * send http request
                     *
                     * Before any application data is sent or received, the
                     * SSL socket will do SSL handshaking first to set up
                     * the security attributes.
                     *
                     * SSL handshaking can be initiated by either flushing data
                     * down the pipe, or by starting the handshaking by hand.
                     *
                     * Handshaking is started manually in this example because
                     * PrintWriter catches all IOExceptions (including
                     * SSLExceptions), sets an internal error flag, and then
                     * returns without rethrowing the exception.
                     *
                     * Unfortunately, this means any error messages are lost,
                     * which caused lots of confusion for others using this
                     * code.  The only way to tell there was an error is to call
                     * PrintWriter.checkError().
                     */
                    socket.startHandshake();
                    PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));

                    out.println("GET / HTTP/1.1");
                    out.println();
                    out.flush();

                    if (out.checkError())
                        System.out.println(
                                "SSLSocketClient:  java.io.PrintWriter error");

                    /* read response */
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream()));

                    String inputLine;
                    int line=1;
                    while ((inputLine = in.readLine()) != null && line<3)  {
                        System.out.println(inputLine);
                        line += line;
                    }

                    in.close();
                    out.close();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    private void doTunnelHandshake(Socket tunnel, String host, int port) throws IOException {
        OutputStream out = tunnel.getOutputStream();
        String msg = "CONNECT " + host + ":" + port + " HTTP/1.1\n\n";
        byte b[];
        try {
            b = msg.getBytes("ASCII7");
        } catch (UnsupportedEncodingException ignored) {
            b = msg.getBytes();
        }
        out.write(b);
        out.flush();

        byte            reply[] = new byte[200];
        int             replyLen = 0;
        int             newlinesSeen = 0;
        boolean         headerDone = false;     /* Done on first newline */

        InputStream in = tunnel.getInputStream();
        boolean         error = false;

        while (newlinesSeen < 2) {
            int i = in.read();
            if (i < 0) {
                throw new IOException("Unexpected EOF from proxy");
            }
            if (i == '\n') {
                headerDone = true;
                ++newlinesSeen;
            } else if (i != '\r') {
                newlinesSeen = 0;
                if (!headerDone && replyLen < reply.length) {
                    reply[replyLen++] = (byte) i;
                }
            }
        }
        String replyStr;
        try {
            replyStr = new String(reply, 0, replyLen, "ASCII7");
        } catch (UnsupportedEncodingException ignored) {
            replyStr = new String(reply, 0, replyLen);
        }

        /* We asked for HTTP/1.0, so we should get that back */
        if (!replyStr.startsWith("HTTP/1.1 200")) {
            throw new IOException("Unable to tunnel through "
                    + tunnelHost + ":" + tunnelPort
                    + ".  Proxy returns \"" + replyStr + "\"");
        }
    }
}