package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableMap;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.concurrent.TimeoutException;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
class MsgObj{
    public String msgID = null;
    public String msgInitiator = null; //ADVnum
    public String msgText = null;
    public String msgType = null; //0: from client; 1: proposal; 2: agreed priority; 3; isDeliverable
    public String msgPriority = null;
    public List<String> replyRcvdFrom;

    public MsgObj(String msgID, String msgType, String msgInitiator, String msgPriority, String msgText){
        this.msgID = msgID;
        this.msgType = msgType;
        this.msgInitiator = msgInitiator;
        this.msgPriority = msgPriority;
        this.msgText = msgText;
        this.replyRcvdFrom = new ArrayList<String>();
    }
    public String createMsg(){
        String msg = msgID+"~~"+msgType+"~~"+msgInitiator+"~~"+msgPriority+"~~"+msgText;
        return msg;
    }
    public static Comparator<MsgObj> sortHoldBackList = new Comparator<MsgObj>() {
        public int compare(MsgObj msg1,MsgObj msg2) {
            if(Double.parseDouble(msg1.msgPriority) < Double.parseDouble(msg2.msgPriority)) return -1;
            if(Double.parseDouble(msg1.msgPriority) > Double.parseDouble(msg2.msgPriority)) return 1;
            return 0;
        }
    };
}

public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    //private static String[] REMOTE_PORT = {"11108","11112","11116","11120","11124"};
    static ArrayList<String> REMOTE_PORT = new ArrayList<String>(Arrays.asList("11108", "11112", "11116", "11120", "11124"));
    static final int SERVER_PORT = 10000;
    static private int keyCounter = 0;
    static private int activeNodes = REMOTE_PORT.size();
    private int msgID = 0;
    private int proposedPriority = 1;
    private String myAVD = null;
    private List<MsgObj> holdBackList = new ArrayList<MsgObj>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        final String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        myAVD = portStr;
        Log.d("portStr", portStr);
        Log.d("myPort", myPort);
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {

            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        final EditText editText = (EditText) findViewById(R.id.editText1);
        editText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    /*
                     * If the key is pressed (i.e., KeyEvent.ACTION_DOWN) and it is an enter key
                     * (i.e., KeyEvent.KEYCODE_ENTER), then we display the string. Then we create
                     * an AsyncTask that sends the string to the remote AVD.
                     */
                    String msg = editText.getText().toString() + "\n";
                    editText.setText(""); // This is one way to reset the input box.
                    TextView localTextView = (TextView) findViewById(R.id.textView1);
                    localTextView.append("\t" + msg); // This is one way to display a string.

                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                    return true;
                }
                return false;
            }
        });

        final Button sendbtn = (Button) findViewById(R.id.button4);
        sendbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString() + "\n";
                editText.setText(""); // This is one way to reset the input box.
                TextView localTextView = (TextView) findViewById(R.id.textView1);
                localTextView.append("\t" + msg); // This is one way to display a string.

                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                //return true;
            }
        });
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            while(true) {
                try{
                    Socket acceptSocket = serverSocket.accept();
                    //acceptSocket.setSoTimeout(500);
                    BufferedReader bis = new BufferedReader(new InputStreamReader(acceptSocket.getInputStream(),"UTF-8"));
                    String rcvMsg = bis.readLine().trim();
                    //Log.d("rcvMsg",rcvMsg);
                    rcvMsg = rcvMsg.substring(rcvMsg.indexOf("G")+1, rcvMsg.length());
                    String[] parseMsg = rcvMsg.split("~~");
                    MsgObj msgObj = new MsgObj(parseMsg[0],parseMsg[1],parseMsg[2],parseMsg[3],parseMsg[4]);
                    //Log.d("Test", Arrays.toString(parseMsg));

                    if(msgObj.msgType.equals("0")){
                        //Log.d("Message-0",msgObj.createMsg());
                        proposedPriority++;
                        msgObj.msgPriority=String.valueOf(proposedPriority).intern()+"."+myAVD;
                        msgObj.msgType="1";
                        if(!msgObj.msgInitiator.equals(myAVD)){
                            holdBackList.add(msgObj);
                        }
                        sendPriority(msgObj);
                    }
                    else if(msgObj.msgType.equals("1")){
                        //sLog.d("Message-1",msgObj.createMsg());
                        for(MsgObj getMsg : holdBackList){
                            if(getMsg.msgInitiator.equals(myAVD) && getMsg.msgID.equals(msgObj.msgID)){
                                //Log.d("StoredMsg",getMsg.createMsg());
                                String proposalFrom = msgObj.msgPriority.split("\\.")[1];
                                if(!getMsg.replyRcvdFrom.contains(proposalFrom)) getMsg.replyRcvdFrom.add(proposalFrom);
                                if(Double.parseDouble(msgObj.msgPriority) > Double.parseDouble(getMsg.msgPriority)){
                                    getMsg.msgPriority = msgObj.msgPriority;
                                }
                                if(getMsg.replyRcvdFrom.size() == activeNodes){
                                    //Log.d("Message-2",msgObj.createMsg());
                                    getMsg.msgType="2";
                                    getMsg.replyRcvdFrom.clear();
                                    multicast(getMsg.createMsg());
                                }
                                break;
                            }
                        }
                    }
                    else if(msgObj.msgType.equals("2")){
                        //Log.d("Message-3",msgObj.createMsg());
                        if((int)Math.floor(Double.parseDouble(msgObj.msgPriority)) > proposedPriority){
                            proposedPriority = (int) Math.floor(Double.parseDouble(msgObj.msgPriority));
                        }
                        for (MsgObj getMsg : holdBackList){
                            if(getMsg.msgInitiator.equals(msgObj.msgInitiator) && getMsg.msgID.equals(msgObj.msgID)){
                                getMsg.msgPriority = msgObj.msgPriority;
                                getMsg.msgType = "3";
                                Collections.sort(holdBackList, MsgObj.sortHoldBackList);
                                break;
                            }
                        }
                    }
                    Iterator<MsgObj> itr = holdBackList.iterator();
                    while(itr.hasNext()){
                        MsgObj getMsg = itr.next();
                        if(getMsg.msgType.equals("3")){
                            publishProgress(getMsg.msgText);
                            itr.remove();
                        }
                        else{
                            break;
                        }
                    }
                    acceptSocket.close();
                } catch(SocketTimeoutException s)
                {
                    System.out.println("Socket timed out!");
                    break;
                }catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
            return null;
        }
        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            //Log.v(TAG, String.valueOf(strReceived));
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");

            Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");

            ContentValues keyValueToInsert = new ContentValues();
            // inserting <”keytoinsert”,“valuetoinsert”>
            keyValueToInsert.put("key", Integer.toString(keyCounter++));
            keyValueToInsert.put("value", strReceived);
            getContentResolver().insert(mUri, keyValueToInsert);

            return;
        }
    }
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }
    private void sendPriority(MsgObj msgObj){
        String thisPort = null;
        try{
            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                    Integer.parseInt(String.valueOf((Integer.parseInt(msgObj.msgInitiator) * 2))));
            thisPort = String.valueOf((Integer.parseInt(msgObj.msgInitiator) * 2));
            socket.setSoTimeout(500);
            socket.setTcpNoDelay(true);
            OutputStream outputStream = socket.getOutputStream();
            DataOutputStream dos = new DataOutputStream(outputStream);
            dos.writeUTF("MSG"+msgObj.createMsg());
            socket.close();
        } catch(SocketTimeoutException s){
            Log.e(TAG, "SocketTimeout Exception at port "+thisPort);
            handelFailure(thisPort);
        } catch(SocketException se){
            Log.e(TAG, "Socket Exception at port "+thisPort);
            handelFailure(thisPort);
        } catch (UnknownHostException e) {
            Log.e(TAG, "Server UnknownHostException"+thisPort);
            handelFailure(thisPort);
        } catch (IOException e) {
            Log.e(TAG, "Server socket IOException"+thisPort);
            handelFailure(thisPort);
        }
    }
    private void multicast(String msg){
        String thisPort = null;
        try {
            for(int i=0;i<REMOTE_PORT.size();i++){
                String remotePort = REMOTE_PORT.get(i);
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(remotePort));
                thisPort = remotePort;
                socket.setSoTimeout(500);
                socket.setTcpNoDelay(true);
                OutputStream outputStream = socket.getOutputStream();
                DataOutputStream dos = new DataOutputStream(outputStream);
                dos.writeUTF("MSG"+msg);
                socket.close();
            }
        } catch(SocketTimeoutException s){
            Log.e(TAG, "SocketTimeout Exception at port "+thisPort);
            handelFailure(thisPort);
        } catch(SocketException se){
            Log.e(TAG, "Socket Exception at port "+thisPort);
            handelFailure(thisPort);
        } catch (UnknownHostException e) {
            Log.e(TAG, "ClientTask UnknownHostException"+thisPort);
            handelFailure(thisPort);
        } catch (IOException e) {
            Log.e(TAG, "ClientTask socket IOException"+thisPort);
            handelFailure(thisPort);
        }
    }
    private void handelFailure(String port){
        Log.d("FailedNode", port);
        if(REMOTE_PORT.contains(port)){
            REMOTE_PORT.remove(port);
            activeNodes = REMOTE_PORT.size();
            for(MsgObj getMsg : holdBackList){
                if(getMsg.replyRcvdFrom.size()==4 && !getMsg.replyRcvdFrom.contains(port) && getMsg.msgType.equals("1")){
                    getMsg.msgType="0";
                    getMsg.replyRcvdFrom.clear();
                    multicast(getMsg.createMsg());
                }
            }
        }
    }
    private class ClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            String msgToSend = msgs[0];
            MsgObj msgObj = new MsgObj(String.valueOf(++msgID).intern(), String.valueOf(0).intern(), myAVD, String.valueOf(proposedPriority).intern() + "." + myAVD, msgToSend);
            holdBackList.add(msgObj);
            multicast(msgObj.createMsg());

            return null;
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
