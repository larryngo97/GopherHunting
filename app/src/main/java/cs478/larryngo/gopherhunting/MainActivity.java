package cs478.larryngo.gopherhunting;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "PROJ4";
    private Context context;
    private final int BOARD_WIDTH = 10; //width of the board
    private final int BOARD_HEIGHT = 10; //height of the board
    private int MOVE_DELAY; //delay for each thread work. Changes depending on gamemode
    private int GAMEMODE = 0; //gamemode type
    private final int GAMEMODE_MOVEBYMOVE = 0; //guess by guess
    private final int GAMEMODE_CONTINUOUS = 1; //continuous
    private boolean GAME_INPROGRESS; //determines if the game is running
    private int currentTurn; //current turn
    private boolean WINNER_FOUND; //lets the game know a winner has been found

    private TextView tv_mode; //Sees the current gamemode
    private TextView tv_console; //sees current move of the threads
    protected static String text_log = ""; //contains all the moves that the threads have made
    private int board[][] = new int[BOARD_WIDTH + 1][BOARD_HEIGHT + 1]; //backboard (for determining what kind of space is clicked)
    private ImageView[][]  imageBoard = new ImageView[BOARD_WIDTH + 1][BOARD_HEIGHT + 1]; //frontboard (displays the board)
    private LinearLayout layoutBoard; //lays out the board
    private ArrayList<Integer> cellImages = new ArrayList<Integer>(Arrays.asList(R.drawable.rect_white, R.drawable.rect_red, R.drawable.rect_orange,
            R.drawable.rect_yellow, R.drawable.rect_blue, R.drawable.rect_green)); //pictures for the board

    //current gopher location used to determine the next move
    private int GOPHER_LOCATION_X;
    private int GOPHER_LOCATION_Y;

    //Responses of the space
    private final int RESPONSE_NULL = 0; //empty space, question mark
    private final int RESPONSE_DISASTER = 1; //disaster space, red
    private final int RESPONSE_COMPLETE_MISS = 2; //complete miss, orange
    private final int RESPONSE_CLOSE_GUESS = 3; //close guess, yellow
    private final int RESPONSE_NEAR_MISS = 4; //near miss, blue
    private final int RESPONSE_SUCCESS = 5; //success, green
    private int RESPONSE_CURRENT; //used for the workers to determine their next move

    //Used by the main thread
    private final int UI_UPDATE_LOG = 1;
    private final int UI_CHECK_WINNER = 2;
    private final int UI_NEXT_MOVE = 3;
    private final int UI_STOP = 4;


    //Used by the worker threads
    private final int MAKE_MOVE = 1;
    private final int MAKE_STOP = 2;


    protected MainThreadHandler mainThread;
    protected WorkerThread1 workerThread1;
    protected WorkerThread2 workerThread2;
    private boolean runningThread = false;

    //Reference of the next-move algorithm. Not used in this program
    private final int[][] ARRAY_NEAR_MISS = {
            {-1, -1}, {-1, 0}, {-1, 1},
            { 0, -1},  /*SC*/  { 0, 1},
            { 1, -1}, { 1, 0}, { 1, 1}
    };

    private final int[][] ARRAY_CLOSE_GUESS = {
            {-2, -2}, {-2, -1}, {-2, 0}, {-2, 1}, {-2, 2},
            {-1, -2},/*{-1, -1} {-1, 0} {-1, 1}*/ { 1, 2},
            { 0, -2},/*{ 0, -1}    SC   { 0, 1}*/ { 0, 2},
            { 1, -2},/*{ 1, -1} { 1, 0} { 1, 1}*/ { 1, 2},
            { 2, -2}, { 2, -1}, { 2, 0}, { 2, 1}, { 2, 2}
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv_console = findViewById(R.id.tv_info);
        tv_mode = findViewById(R.id.tv_mode);
        GAME_INPROGRESS = false;
        mainThread = new MainThreadHandler();
        workerThread1 = new WorkerThread1(mainThread);
        workerThread2 = new WorkerThread2(mainThread);
        workerThread1.start();
        workerThread2.start();

        TextView tv_console = findViewById(R.id.tv_info);
        tv_console.setText("Turn: 0");
        GAMEMODE = GAMEMODE_MOVEBYMOVE;
        tv_mode.setText("GAMEMODE: MOVE-BY-MOVE");
        context = this;
        resetBoard();

        Button button_start = findViewById(R.id.button_startGame);
        button_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!GAME_INPROGRESS) //if game hasnt been started, start the game
                {
                    GAME_INPROGRESS = true;
                    Toast.makeText(getApplicationContext(), "Game started!", Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "+++ STARTING A NEW GAME +++");
                    resetGame();
                }
                else //if game has been started, stop the game
                {
                    runningThread = false;
                    GAME_INPROGRESS = false;
                    if(GAMEMODE == GAMEMODE_CONTINUOUS) //if its continuous, stop the threads immediately
                    {
                        workerThread1.interrupt();
                        workerThread2.interrupt();
                    }

                    Toast.makeText(getApplicationContext(), "Game stopped!", Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "+++ STOPPING CURRENT GAME +++");
                }
            }
        });

        Button button_mode = findViewById(R.id.button_mode);
        button_mode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!GAME_INPROGRESS) //while game is not started, change gamemode
                {
                    changeGamemode();
                }
                else //does not do anything. Waits for the user to stop game
                {
                    Toast.makeText(getApplicationContext(), "Please stop the game first!", Toast.LENGTH_SHORT).show();
                }
                /*Below code is to change the gamemode regardless of the game state. Remove above code to apply
                changeGamemode();
                if(GAMEMODE == GAMEMODE_CONTINUOUS)
                {
                    if(currentTurn % 2 == 1)
                    {
                       Message m = workerThread1.workerThread1Handler.obtainMessage(MAKE_MOVE);
                       workerThread1.workerThread1Handler.sendMessage(m);
                    }
                    else if (currentTurn % 2 == 0)
                    {
                       Message m = workerThread2.workerThread2Handler.obtainMessage(MAKE_MOVE);
                       workerThread2.workerThread2Handler.sendMessage(m);
                    }
                }
                */
            }
        });

        Button button_nextMove = findViewById(R.id.button_nextmove);
        button_nextMove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(GAMEMODE == GAMEMODE_MOVEBYMOVE) //only works if the gamemode is move-by-move
                {
                    if(GAME_INPROGRESS) //during game
                    {
                        //makes move based on the current turn
                        if(currentTurn % 2 == 1)
                        {
                            Message m = workerThread1.workerThread1Handler.obtainMessage(MAKE_MOVE);
                            workerThread1.workerThread1Handler.sendMessage(m);
                        }
                        else if (currentTurn % 2 == 0)
                        {
                            Message m = workerThread2.workerThread2Handler.obtainMessage(MAKE_MOVE);
                            workerThread2.workerThread2Handler.sendMessage(m);
                        }
                    }
                    else
                    {
                        Toast.makeText(getApplicationContext(), "Game not in progress! Start another one!", Toast.LENGTH_SHORT).show();
                    }
                }
                else
                {
                    Toast.makeText(getApplicationContext(), "Gamemode must be Move-By-Move!", Toast.LENGTH_SHORT).show();
                }
            }
        });


        Button button_legend = findViewById(R.id.button_legend);
        button_legend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), HelpActivity.class);
                startActivity(intent);
            }
        });

        Button button_log = findViewById(R.id.button_log);
        button_log.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), LogActivity.class);
                startActivity(intent);
            }
        });
    }

    public void changeGamemode()
    {
        if(GAMEMODE == GAMEMODE_MOVEBYMOVE)
        {
            Log.i(TAG, "Changing gamemode to Continuous");
            GAMEMODE = GAMEMODE_CONTINUOUS;
            MOVE_DELAY = 2000; //allows user to see the changes
            tv_mode.setText("GAMEMODE: CONTINUOUS");
        }
        else
        {
            Log.i(TAG, "Changing gamemode to Move By Move");
            GAMEMODE = GAMEMODE_MOVEBYMOVE;
            MOVE_DELAY = 50; //allows almost instantaneous changes
            tv_mode.setText("GAMEMODE: MOVE-BY-MOVE");
        }
    }

    public void resetGame()
    {
        stopThreads(); //stop current threads
        runningThread = true; //threads are allowed to run
        tv_console.setText("Turn: 0");
        currentTurn = 1;
        WINNER_FOUND = false;
        RESPONSE_CURRENT = RESPONSE_NULL;
        text_log = "";
        resetBoard();
        insertGopher();
        printBoard();

        if(GAMEMODE == GAMEMODE_CONTINUOUS) //continuous gameplay
        {
            Message m = workerThread1.workerThread1Handler.obtainMessage(MAKE_MOVE);
            workerThread1.workerThread1Handler.sendMessage(m);
        }
    }

    public void resetBoard()
    {
        layoutBoard = (LinearLayout) findViewById(R.id.layout_board);
        layoutBoard.removeAllViews();
        int cellSize = Math.round(context.getResources().getDisplayMetrics().widthPixels / (float)BOARD_WIDTH); //gets width size of phone and divides by num of cells
        LinearLayout.LayoutParams boardRow = new LinearLayout.LayoutParams(cellSize * BOARD_WIDTH, cellSize); //makes a row
        LinearLayout.LayoutParams boardCell = new LinearLayout.LayoutParams(cellSize, cellSize); //cell size height and width the same


        for (int i = 1; i <= BOARD_WIDTH; i++)
        {
            LinearLayout cellRow = new LinearLayout(context);
            for(int j = 1; j <= BOARD_HEIGHT; j++)
            {
                board[i][j] = RESPONSE_COMPLETE_MISS; //all spaces are complete misses
                imageBoard[i][j] = new ImageView(context);

                imageBoard[i][j].setImageResource(cellImages.get(RESPONSE_NULL)); //sets all cells to white
                cellRow.addView(imageBoard[i][j], boardCell);
            }
            layoutBoard.addView(cellRow, boardRow);
        }
    }

    public void insertGopher()
    {
        Random r = new Random();
        GOPHER_LOCATION_X = r.nextInt(BOARD_WIDTH) + 1; //ranges from 1-10
        GOPHER_LOCATION_Y = r.nextInt(BOARD_HEIGHT) + 1; //ranges from 1-10

        board[GOPHER_LOCATION_X][GOPHER_LOCATION_Y] = RESPONSE_SUCCESS; //gopher is added into this hole

        //Close Guess Proximities
        for(int i = GOPHER_LOCATION_X - 2; i <= GOPHER_LOCATION_X + 2; i++)
        {
            for(int j = GOPHER_LOCATION_Y - 2; j <= GOPHER_LOCATION_Y + 2; j++)
            {
                if(i != GOPHER_LOCATION_X || j != GOPHER_LOCATION_Y) //makes sure to not place in the gopher's location
                {
                    if(i <= BOARD_WIDTH && i > 0 && j <= BOARD_HEIGHT && j > 0) //makes sure the coordinates are within the board
                    {
                        board[i][j] = RESPONSE_CLOSE_GUESS; //this space occupies the close guess state
                    }
                }
            }
        }

        //Near Miss Proximities
        for(int i = GOPHER_LOCATION_X - 1; i <= GOPHER_LOCATION_X + 1; i++)
        {
            for(int j = GOPHER_LOCATION_Y - 1; j <= GOPHER_LOCATION_Y + 1; j++)
            {
                if(i != GOPHER_LOCATION_X || j != GOPHER_LOCATION_Y) //makes sure to not place in the gopher's location
                {
                    if(i <= BOARD_WIDTH && i > 0 && j <= BOARD_HEIGHT && j > 0) //makes sure the coordinates are within the board
                    {
                        board[i][j] = RESPONSE_NEAR_MISS; //this space occupies the near miss state
                    }
                }
            }
        }

        System.out.println("Gopher added at hole: " + GOPHER_LOCATION_X + ", " + GOPHER_LOCATION_Y);
    }

    public void printBoard()
    {
        String[] row = {"?", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
        System.out.println("+++ PRINTING BOARD +++");
        System.out .println("  1 2 3 4 5 6 7 8 9 0");
        for(int i = 1; i <= BOARD_WIDTH; i++)
        {
            System.out.print(row[i] + " ");
            for (int j = 1; j <= BOARD_HEIGHT; j++)
            {
                System.out.print(board[i][j] + " ");
            }
            System.out.println();
        }
    }


    private class MainThreadHandler extends Handler
    {
        private TextView tv_console = findViewById(R.id.tv_info);
        public void handleMessage(Message msg)
        {
            int what = msg.what;
            Message m;
            switch(what) {
                case UI_UPDATE_LOG:
                    if(LogActivity.tv_log != null)
                    {
                        LogActivity.tv_log.setText(text_log);
                    }
                    break;
                case UI_STOP: //nothing, useless
                    break;
                case UI_CHECK_WINNER: //checks if theres a winner every turn. not really necessary
                    if(WINNER_FOUND)
                    {
                        currentTurn--; //goes to previous turn to correctly determine winner
                        if(currentTurn % 2 == 1) //player 1
                        {
                            Log.i(TAG, "Player 1 Won!");
                            Toast.makeText(getApplicationContext(), "Thread 1 Won!", Toast.LENGTH_SHORT).show();
                            tv_console.setText("Turn: " + currentTurn + "(P1)\nMove: Winner!");
                        }
                        else if (currentTurn % 2 == 0) //player 2
                        {
                            Log.i(TAG, "Player 2 Won!");
                            Toast.makeText(getApplicationContext(), "Thread 2 Won!", Toast.LENGTH_SHORT).show();
                            tv_console.setText("Turn: " + currentTurn + "(P2)\nMove: Winner!");
                        }
                        //stop the worker threads
                        m = workerThread1.workerThread1Handler.obtainMessage(MAKE_STOP);
                        workerThread1.workerThread1Handler.sendMessage(m);
                        m = workerThread2.workerThread2Handler.obtainMessage(MAKE_STOP);
                        workerThread2.workerThread2Handler.sendMessage(m);
                        GAME_INPROGRESS = false; //game no longer in progress
                    }
                    else //if no winner was found, go to next move
                    {
                        if(GAMEMODE == GAMEMODE_CONTINUOUS) //only for continuous gamemode. move-by-move should be using the button
                        {
                            m = mainThread.obtainMessage(UI_NEXT_MOVE);
                            mainThread.sendMessage(m);
                        }
                    }
                    break;
                case UI_NEXT_MOVE:
                    //for continuous gamemode
                    //determines which thread will make the next move
                    if(currentTurn % 2 == 1)
                    {
                        m = workerThread1.workerThread1Handler.obtainMessage(MAKE_MOVE);
                        workerThread1.workerThread1Handler.sendMessage(m);
                    }
                    else if (currentTurn % 2 == 0)
                    {
                        m = workerThread2.workerThread2Handler.obtainMessage(MAKE_MOVE);
                        workerThread2.workerThread2Handler.sendMessage(m);
                    }
                    break;
            }
        }
    }


    private class WorkerThread1 extends Thread {
        private Handler workerThread1Handler;
        private MainThreadHandler handlerThread;
        private TextView tv_console = findViewById(R.id.tv_info);

        private WorkerThread1(MainThreadHandler ht)
        {
            handlerThread = ht;
        }

        private synchronized void makeMove()
        {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    int x;
                    int y;
                    String current_player = "(P1)";
                    String messageTurnPlayer = "Turn: " + currentTurn + current_player + "\n";
                    String messageNextMove = "NULL";
                    String messageMoveStatus = "NULL";
                    Log.i(TAG,"Turn: " + currentTurn + current_player);

                    Log.i(TAG, "Current Response: " + RESPONSE_CURRENT);

                    if(RESPONSE_CURRENT == RESPONSE_SUCCESS) //should not happen
                    {
                        Log.i(TAG, "Gopher already found");
                        tv_console.setText("Turn: " + currentTurn + current_player + "\nWinner!");
                        return;
                    }
                    else if(RESPONSE_CURRENT == RESPONSE_NEAR_MISS) //next move will be around the proximities of 1 space
                    {
                        Log.i(TAG, "Gopher near miss");
                        messageNextMove = "Move Used: 1 Spot from last move\n";
                        Random r = new Random();
                        x = r.nextInt((GOPHER_LOCATION_X + 2) - (GOPHER_LOCATION_X -1)) + (GOPHER_LOCATION_X-1);
                        y = r.nextInt((GOPHER_LOCATION_Y + 2) - (GOPHER_LOCATION_Y -1)) + (GOPHER_LOCATION_Y-1);
                    }
                    else if(RESPONSE_CURRENT == RESPONSE_CLOSE_GUESS) //next move will be around the proximities of 2 spaces
                    {
                        Log.i(TAG, "Gopher close guess");
                        messageNextMove = "Move Used: 2 Spots from last move\n";
                        Random r = new Random();
                        x = r.nextInt((GOPHER_LOCATION_X + 2) - (GOPHER_LOCATION_X -1)) + (GOPHER_LOCATION_X-1);
                        y = r.nextInt((GOPHER_LOCATION_Y + 2) - (GOPHER_LOCATION_Y -1)) + (GOPHER_LOCATION_Y-1);
                    }
                    else //complete misses, disasters, or start of game makes a random move
                    {
                        Log.i(TAG, "Making a random move");
                        messageNextMove = "Move Used: Random\n";
                        Random r = new Random();
                        x = r.nextInt(BOARD_WIDTH) + 1; //ranges from 1-10
                        y = r.nextInt(BOARD_HEIGHT) + 1; //ranges from 1-10
                    }

                    //making sure the coordinates don't go out of bounds
                    if(x < 0)
                        x = 0;
                    if(x > BOARD_WIDTH)
                        x = BOARD_WIDTH;
                    if(y < 0)
                        y = 0;
                    if(y > BOARD_HEIGHT)
                        y = BOARD_HEIGHT;

                    //X AND Y DECIDED AT THIS POINT//
                    final ImageView iv = imageBoard[x][y]; //gets image at board

                    Log.i(TAG, "Current Gopher Location: " + GOPHER_LOCATION_X + ", " + GOPHER_LOCATION_Y);
                    Log.i(TAG, "Moving to " + x + ", " + y);

                    if(board[x][y] == RESPONSE_SUCCESS)
                    {
                        Log.i(TAG, "Current move was a SUCCESS");
                        messageMoveStatus = "Move Status: SUCCESS\n";
                        RESPONSE_CURRENT = RESPONSE_SUCCESS;
                        WINNER_FOUND = true;
                    }
                    else if(board[x][y] == RESPONSE_NEAR_MISS)
                    {
                        Log.i(TAG, "Current move was a NEAR MISS");
                        messageMoveStatus = "Move Status: NEAR MISS\n";
                        RESPONSE_CURRENT = RESPONSE_NEAR_MISS;
                    }
                    else if(board[x][y] == RESPONSE_CLOSE_GUESS)
                    {
                        Log.i(TAG, "Current move was a CLOSE GUESS");
                        messageMoveStatus = "Move Status: CLOSE GUESS\n";
                        RESPONSE_CURRENT = RESPONSE_CLOSE_GUESS;
                    }
                    else if(board[x][y] == RESPONSE_COMPLETE_MISS)
                    {
                        Log.i(TAG, "Current move was a COMPLETE MISS");
                        messageMoveStatus = "Move Status: COMPLETE MISS\n";
                        RESPONSE_CURRENT = RESPONSE_COMPLETE_MISS;
                    }
                    else if(board[x][y] == RESPONSE_DISASTER)
                    {
                        Log.i(TAG, "Current move was a DISASTER");
                        messageMoveStatus = "Move Status: DISASTER\n";
                        RESPONSE_CURRENT = RESPONSE_DISASTER;
                    }
                    board[x][y] = RESPONSE_DISASTER; //set to already clicked

                    if(messageMoveStatus.equals("NULL"))
                    {
                        currentTurn--;
                    }
                    final String messageLog = messageTurnPlayer + messageNextMove + messageMoveStatus + "\n";
                    //depending on the state of the board, set the correct color
                    mainThread.post(new Runnable() {
                        @Override
                        public void run() {
                            tv_console.setText(messageLog);
                            text_log = text_log + messageLog;
                            Log.i(TAG, text_log);
                            if(iv != null)
                            {
                                iv.setImageResource(cellImages.get(RESPONSE_CURRENT));
                            }
                            else
                            {
                                Log.i(TAG, "Something went wrong when setting image");
                            }
                        }
                    });

                    //If gopher hasn't been spotted yet, change the cell back to default
                    mainThread.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (iv != null)
                            {
                                iv.setImageResource(cellImages.get(RESPONSE_NULL));
                                Log.i(TAG, "Something went wrong when setting image to default");
                            }
                        }
                    }, 1500);
                    currentTurn++;
                }
            });
        }

        @Override
        public void run() {
            Looper.prepare();

            workerThread1Handler = new Handler()
            {
                @Override
                public void handleMessage(Message msg) {
                    if(!runningThread)
                    {
                        return;
                    }
                    int what = msg.what;
                    Message m;
                    switch(what)
                    {
                        case MAKE_MOVE:
                            workerThread1Handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    try{ Thread.sleep(MOVE_DELAY); }
                                    catch (InterruptedException e) { System.out.println("Thread interrupted!"); }

                                    Message m;
                                    if(!WINNER_FOUND) //if winner hasnt been found yet, make the next move
                                    {
                                        makeMove();
                                        printBoard();
                                        m = mainThread.obtainMessage(UI_UPDATE_LOG);
                                        mainThread.sendMessage(m);

                                        m = handlerThread.obtainMessage(UI_CHECK_WINNER); //after making the move, check to see if theres a winner
                                        handlerThread.sendMessage(m);
                                    }

                                }
                            });
                            break;
                        case MAKE_STOP:
                            workerThread1Handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Log.i(TAG, "Thread 1: Game over");
                                    try { Thread.sleep(MOVE_DELAY); }
                                    catch (InterruptedException e) { System.out.println("Thread interrupted!"); }
                                }
                            });
                            break;
                    }
                }
            };
            Looper.loop();
        }
    }

    private class WorkerThread2 extends Thread {
        private Handler workerThread2Handler;
        private MainThreadHandler handlerThread;
        private TextView tv_console = findViewById(R.id.tv_info);

        private WorkerThread2(MainThreadHandler ht)
        {
            handlerThread = ht;
        }

        private synchronized void makeMove()
        {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    int x;
                    int y;
                    String current_player = "(P2)";
                    String messageTurnPlayer = "Turn: " + currentTurn + current_player + "\n";
                    String messageNextMove = "NULL";
                    String messageMoveStatus = "NULL";
                    Log.i(TAG,"Turn: " + currentTurn + current_player);

                    Log.i(TAG, "Current Response: " + RESPONSE_CURRENT);

                    if(RESPONSE_CURRENT == RESPONSE_SUCCESS) //should not happen
                    {
                        Log.i(TAG, "Gopher already found");
                        tv_console.setText("Turn: " + currentTurn + current_player + "\nWinner!");
                        return;
                    }
                    else if(RESPONSE_CURRENT == RESPONSE_NEAR_MISS) //next move will be around the proximities of 1 space
                    {
                        Log.i(TAG, "Gopher near miss");
                        messageNextMove = "Move Used: 1 Spot from last move\n";
                        Random r = new Random();
                        x = r.nextInt((GOPHER_LOCATION_X + 2) - (GOPHER_LOCATION_X -1)) + (GOPHER_LOCATION_X-1);
                        y = r.nextInt((GOPHER_LOCATION_Y + 2) - (GOPHER_LOCATION_Y -1)) + (GOPHER_LOCATION_Y-1);
                    }
                    else if(RESPONSE_CURRENT == RESPONSE_CLOSE_GUESS) //next move will be around the proximities of 2 spaces
                    {
                        Log.i(TAG, "Gopher close guess");
                        messageNextMove = "Move Used: 2 Spots from last move\n";
                        Random r = new Random();
                        x = r.nextInt((GOPHER_LOCATION_X + 2) - (GOPHER_LOCATION_X -1)) + (GOPHER_LOCATION_X-1);
                        y = r.nextInt((GOPHER_LOCATION_Y + 2) - (GOPHER_LOCATION_Y -1)) + (GOPHER_LOCATION_Y-1);
                    }
                    else //complete misses, disasters, or start of game makes a random move
                    {
                        Log.i(TAG, "Making a random move");
                        messageNextMove = "Move Used: Random\n";
                        Random r = new Random();
                        x = r.nextInt(BOARD_WIDTH) + 1; //ranges from 1-10
                        y = r.nextInt(BOARD_HEIGHT) + 1; //ranges from 1-10
                    }

                    //making sure the coordinates don't go out of bounds
                    if(x < 0)
                        x = 0;
                    if(x > BOARD_WIDTH)
                        x = BOARD_WIDTH;
                    if(y < 0)
                        y = 0;
                    if(y > BOARD_HEIGHT)
                        y = BOARD_HEIGHT;

                    //X AND Y DECIDED AT THIS POINT//
                    final ImageView iv = imageBoard[x][y]; //gets image at board

                    Log.i(TAG, "Current Gopher Location: " + GOPHER_LOCATION_X + ", " + GOPHER_LOCATION_Y);
                    Log.i(TAG, "Moving to " + x + ", " + y);

                    if(board[x][y] == RESPONSE_SUCCESS)
                    {
                        Log.i(TAG, "Current move was a SUCCESS");
                        messageMoveStatus = "Move Status: SUCCESS\n";
                        RESPONSE_CURRENT = RESPONSE_SUCCESS;
                        WINNER_FOUND = true;
                    }
                    else if(board[x][y] == RESPONSE_NEAR_MISS)
                    {
                        Log.i(TAG, "Current move was a NEAR MISS");
                        messageMoveStatus = "Move Status: NEAR MISS\n";
                        RESPONSE_CURRENT = RESPONSE_NEAR_MISS;
                    }
                    else if(board[x][y] == RESPONSE_CLOSE_GUESS)
                    {
                        Log.i(TAG, "Current move was a CLOSE GUESS");
                        messageMoveStatus = "Move Status: CLOSE GUESS\n";
                        RESPONSE_CURRENT = RESPONSE_CLOSE_GUESS;
                    }
                    else if(board[x][y] == RESPONSE_COMPLETE_MISS)
                    {
                        Log.i(TAG, "Current move was a COMPLETE MISS");
                        messageMoveStatus = "Move Status: COMPLETE MISS\n";
                        RESPONSE_CURRENT = RESPONSE_COMPLETE_MISS;
                    }
                    else if(board[x][y] == RESPONSE_DISASTER)
                    {
                        Log.i(TAG, "Current move was a DISASTER");
                        messageMoveStatus = "Move Status: DISASTER\n";
                        RESPONSE_CURRENT = RESPONSE_DISASTER;
                    }
                    board[x][y] = RESPONSE_DISASTER; //set to already clicked

                    if(messageMoveStatus.equals("NULL"))
                    {
                        currentTurn--;
                    }
                    final String messageLog = messageTurnPlayer + messageNextMove + messageMoveStatus + "\n";
                    //depending on the state of the board, set the correct color
                    mainThread.post(new Runnable() {
                        @Override
                        public void run() {
                            tv_console.setText(messageLog);
                            text_log = text_log + messageLog;
                            Log.i(TAG, text_log);
                            if(iv != null)
                            {
                                iv.setImageResource(cellImages.get(RESPONSE_CURRENT));
                            }
                            else
                            {
                                Log.i(TAG, "Something went wrong when setting image");
                            }
                        }
                    });

                    //If gopher hasn't been spotted yet, change the cell back to default
                    mainThread.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (iv != null)
                            {
                                iv.setImageResource(cellImages.get(RESPONSE_NULL));
                                Log.i(TAG, "Something went wrong when setting image to default");
                            }
                        }
                    }, 1500);
                    currentTurn++;
                }
            });

        }

        @Override
        public void run() {
            Looper.prepare();

            workerThread2Handler = new Handler()
            {
                @Override
                public void handleMessage(Message msg) {
                    if(!runningThread)
                    {
                        return;
                    }
                    int what = msg.what;
                    Message m;
                    switch(what)
                    {
                        case MAKE_MOVE:
                            workerThread2Handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    try{ Thread.sleep(MOVE_DELAY); }
                                    catch (InterruptedException e) { System.out.println("Thread interrupted!"); }

                                    Message m;
                                    if(!WINNER_FOUND) //if winner hasnt been found yet, make the next move
                                    {
                                        makeMove();
                                        printBoard();
                                        m = mainThread.obtainMessage(UI_UPDATE_LOG);
                                        mainThread.sendMessage(m);

                                        m = handlerThread.obtainMessage(UI_CHECK_WINNER); //after making the move, check to see if theres a winner
                                        handlerThread.sendMessage(m);
                                    }
                                }
                            });
                            break;
                        case MAKE_STOP:
                            workerThread2Handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Log.i(TAG, "Thread 2: Game over");
                                    try { Thread.sleep(MOVE_DELAY); }
                                    catch (InterruptedException e) { System.out.println("Thread interrupted!"); }
                                }
                            });
                            break;
                    }
                }
            };
            Looper.loop();
        }
    }

    public void stopThreads ()
    {
        workerThread1.workerThread1Handler.removeMessages(MAKE_MOVE);
        workerThread1.workerThread1Handler.removeMessages(MAKE_STOP);
        workerThread2.workerThread2Handler.removeMessages(MAKE_MOVE);
        workerThread2.workerThread2Handler.removeMessages(MAKE_STOP);
        mainThread.removeMessages(UI_CHECK_WINNER);
        mainThread.removeMessages(UI_NEXT_MOVE);
    }


}
