package com.livejournal.karino2.prevsilenceaudioplayer;

/**
 * Created by karino on 1/15/15.
 */
public class Command {
    public enum CommandType {
        STOP,
        PREVIOUS,
        PAUSE,
        NEXT,
        MEDIABUTTON_WAIT,
        NEW_FILE
    }
    public static Command createCommand(CommandType typ) {
        return new Command(typ, null);
    }

    public static Command createNewFileCommand(String file) {
        return new Command(CommandType.NEW_FILE, file);
    }

    String filePath;
    CommandType commandType;

    public String getFilePath() {
        return filePath;
    }

    public CommandType getCommandType() {
        return commandType;
    }

    public Command(CommandType typ, String file) {
        commandType = typ;
        filePath = file;
    }



}
