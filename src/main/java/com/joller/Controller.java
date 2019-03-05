package com.joller;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.joller.asciitomathml.AsciiMathParser;
import com.joller.asciitomathml.XmlUtilities;
import com.joller.calculationparser.Command;
import com.joller.calculationparser.Converter;
import com.joller.calculator.Calculator;
import com.joller.mathmltoword.WordMathWriter;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.poi.ss.formula.FormulaParseException;
import org.w3c.dom.Document;

import static com.joller.calculationparser.Command.*;

/**
 * NOTE: Actions for buttons etc. are set in FXML file!
 */
public class Controller {


    private final String DIRECTORY_HELP = "/helpUI.txt";

    Stage primaryStage = Main.getPrimaryStage();

    private FileChooser fileOpener;
    private FileChooser fileSaver;
    private SimpleObjectProperty<File> lastKnownDirectoryProperty = new SimpleObjectProperty<>();

    @FXML
    private TextArea outputArea;

    @FXML
    private TextArea inputArea;

    @FXML
    private Text fileLocationText;


    @FXML
    void initialize() {
        outputArea.setEditable(false);
        initializeInstructionsTextAndAlert();
        initializeTextFileOpener();
        initializeTextFileSaver();
    }


    @FXML
    void initializeTextFileOpener() {
        fileOpener = new FileChooser();
        fileOpener.initialDirectoryProperty().bindBidirectional(lastKnownDirectoryProperty);
        fileOpener.setTitle("Open Saved Calculations");
        // setting to open only .txt files
        fileOpener.getExtensionFilters().add(new FileChooser.ExtensionFilter("Normal text file","*.txt"));
    }

    @FXML
    void initializeTextFileSaver(){
        fileSaver = new FileChooser();
        fileSaver.initialDirectoryProperty().bindBidirectional(lastKnownDirectoryProperty);
        fileSaver.setTitle("Save Calculations");
        fileSaver.getExtensionFilters().add(new FileChooser.ExtensionFilter("Normal text file","*.txt"));
    }


    private Alert alert;
    void initializeInstructionsTextAndAlert() {
        StringBuilder collect = new StringBuilder();
        try {
            InputStream in = getClass().getResourceAsStream(DIRECTORY_HELP);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                collect.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Instructions");
        alert.setContentText(collect.toString());
    }


    @FXML
    private void showInstructions() {
        alert.showAndWait();
    }


    @FXML
    private void openFile() {
        File file = fileOpener.showOpenDialog((primaryStage));
        try {
            if (file != null) {
                saveLastKnownDirectory(file);
                String savedText = readFile(file);
                displayFileInInputArea(savedText);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String readFile(File file) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            stringBuilder.append(line + "\n");
        }
        bufferedReader.close();
        return stringBuilder.toString();
    }
    private void displayFileInInputArea(String string) {
        inputArea.clear();
        inputArea.setText(string);
    }

    @FXML
    private void saveFile() {
        try {
            File file = fileSaver.showSaveDialog(primaryStage);
            if (file != null) {
                saveLastKnownDirectory(file);
                String text = inputArea.getText();
                FileWriter fileWriter = new FileWriter(file);
                fileWriter.write(text);
                fileWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveLastKnownDirectory(File file) {
        lastKnownDirectoryProperty.setValue(file.getParentFile());
        fileLocationText.setText(file.getAbsolutePath());
    }

    Queue<Command> commandQueue = new LinkedList<>();

    boolean isMathMLParsingAllowed = false;

    @FXML
    private void parse() {
        isMathMLParsingAllowed = false;
        StringBuilder output = new StringBuilder(); // collects lines to output to output area
        Converter converter = new Converter();
        Calculator calculator = new Calculator();
        Command command;

        commandQueue = new LinkedList<>();

        try {
            for (String line : inputArea.getText().split("\\n")) {

                command = converter.getCommandType(line);
                commandQueue.add(command);
                if (command == COMMENT) {
                    output.append(converter.parse(line, COMMENT) + "\n");
                } else {

                    line = line.replaceAll("\\s+","");

                    if (command == SET) {
                        output.append(converter.parse(line, SET) + "\n");

                    } else if (command == CALCF) {
                        String unsortedResult = converter.parse(line, CALCF);
                        String[] convertResultAsArray = unsortedResult.split("\n");
                        String precision = convertResultAsArray[0];
                        String variableName = convertResultAsArray[1];
                        String calculationWithVariableNames = convertResultAsArray[2];
                        String calculationWithoutVariableNames = convertResultAsArray[3];
                        String unit = convertResultAsArray[4];

                        StringBuilder resultCollector = new StringBuilder();
                        calculator.setDecimalPlace(Integer.parseInt(precision));

                        String calculated = calculator.calculate(calculationWithoutVariableNames);

                        resultCollector.append(variableName + '=' + calculationWithVariableNames
                                + '=' + calculationWithoutVariableNames + '=' + calculated + ' ' + unit);

                        // calculate arithmetic and save variable
                        converter.parse(variableName + '=' + calculated + "," + unit, SET);

                        output.append(resultCollector + "\n");
                    } else {
                        throw new IllegalArgumentException(line + "<<< Error: unknown command");
                    }
                }
            }
            outputArea.setText(output.toString());
            outputArea.setScrollTop(Double.MAX_VALUE);
            isMathMLParsingAllowed = true;
        } catch (IllegalArgumentException e) {
            output.append(e.getMessage() + "\n");
            outputArea.setText(output.toString());
            outputArea.setScrollTop(Double.MAX_VALUE);
        } catch (FormulaParseException e) {
            output.append("Trying to calculate with unknown variable, check this line!\n");
            outputArea.setText(output.toString());
            outputArea.setScrollTop(Double.MAX_VALUE);
        } catch (Exception e) {
            e.printStackTrace();
            output.append(e.getMessage() + "\n");
            outputArea.setText(output.toString());
            outputArea.setScrollTop(Double.MAX_VALUE);
        }
    }

    @FXML
    public void formulasToMathMLToWord() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        if (!isMathMLParsingAllowed) {
            alert.setTitle("Warning Dialog");
            alert.setContentText("Cannot parse your calculations to Word!" +
                    "\nHave you fixed errors in your calculations?");
            alert.showAndWait();
        }
        try {
            String lines = outputArea.getText();
            if (lines == null || lines.length() < 1) return;

            final AsciiMathParser mathParser = new AsciiMathParser();
            List<String> list = new ArrayList<>();

            for (String line : lines.split("\\n")) {
                if (commandQueue.poll() == COMMENT) {
                    list.add(line);
                } else {
                    Document result = mathParser.parseAsciiMath(line);
                    list.add(XmlUtilities.serializeMathmlDocument(result));
                }
            }

            WordMathWriter wordMathWriter = new WordMathWriter();
            for (String line : list) {
                wordMathWriter.addLine(line);
            }

            wordMathWriter.write();
        } catch (Exception e) {
            alert.setTitle("Oops... something went wrong!"
            + "\n" + e.getMessage());
        }
    }

}