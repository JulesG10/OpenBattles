package com.julesG10.network;

import com.julesG10.utils.Console;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Client {

    public String hostname;
    public int port;
    public Socket client = new Socket();
    private DataInputStream reader;
    private DataOutputStream writer;
    private long total = 0;
    private long start = 0;

    public Client(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
    }

    public boolean connect(int timeout) {
        try {
            this.client.connect(new InetSocketAddress(this.hostname, this.port), timeout);
            this.reader = new DataInputStream(this.client.getInputStream());
            this.writer = new DataOutputStream(this.client.getOutputStream());
            this.start = System.currentTimeMillis();

            return true;
        } catch (IOException e) {
            Console.log(e.getMessage());
            return false;
        }
    }

    public void close() {
        try {
            this.reader.close();
            this.writer.close();
            this.client.close();
        } catch (IOException e) {
            Console.log(e.getMessage());
        }
    }

    public long getBytePerSecond()
    {
        return (this.total/(System.currentTimeMillis() - this.start));
    }

    public String receive() {
        if (this.client.isConnected()) {
            try {
                int character;
                StringBuilder data = new StringBuilder();
                while ((character = reader.read()) != -1) {
                    if (character == 0) {
                        break;
                    }
                    data.append((char) character);
                }

                this.total += data.toString().getBytes().length;
                return data.toString();
            } catch (IOException e) {
                Console.log(e.getMessage());
                return null;
            }
        }
        return null;
    }

    public boolean send(String data) {
        if (this.client.isConnected()) {
            try {
                writer.writeBytes(data);
                writer.write(0);
                writer.flush();
                return true;
            } catch (IOException e) {
                Console.log(e.getMessage());
                return false;
            }
        }
        return false;
    }
}