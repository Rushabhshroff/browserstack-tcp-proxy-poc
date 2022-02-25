package com.browserstack.tcpproxy;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EditText ipAddressInput = (EditText)findViewById(R.id.editTextTextPersonName4);
        EditText portInput = (EditText)findViewById(R.id.editTextTextPersonName5);
        Button connectButton = (Button)findViewById(R.id.button2);
        connectButton.setOnClickListener((event)->{
            try {
                createTCPConnection(ipAddressInput.getText().toString(), Integer.parseInt(portInput.getText().toString()));
            }catch (Exception ex){
                ex.printStackTrace();
            }
        });
    }

    public Socket createSocket(String host,int port) throws IOException {
        String proxyHost = System.getProperty("http.proxyHost");
        String proxyPort = System.getProperty("http.proxyPort");
        boolean isProxyEnabled = proxyHost != null && proxyPort != null;
        Socket tcpSocket;
        if(isProxyEnabled){
            tcpSocket = new Socket(proxyHost,Integer.parseInt(proxyPort));
            OutputStream output = tcpSocket.getOutputStream();
            byte[] connectMessage = String.format("CONNECT %s:%d HTTP/1.1\n\n", host, port).getBytes();
            output.write(connectMessage);
        }else{
            tcpSocket = new Socket(host,port);
        }
        return tcpSocket;
    }

    public void createTCPConnection(String host,int port) throws Exception{
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket connection = createSocket(host,port);
                    OutputStream output = connection.getOutputStream();
                    InputStream input = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(input));
//                    String line = reader.readLine();
//                    System.out.println(line);
                    for(int i = 0; i < 100; i++){
                        output.write("Hello \n\n".getBytes()); // Message after connection has been established. No HTTP headers
                    }
                    String line = reader.readLine();
                    System.out.println(line);
                    connection.close();
                }catch (Exception ex){
                    ex.printStackTrace();
                }
            }
        }).start();

    }
}