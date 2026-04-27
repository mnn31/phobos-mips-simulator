package modernmars.ui;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;

import modernmars.core.MarsBackend;
import modernmars.util.RegisterNames;

/**
 * Right-hand panel showing the 32 MIPS general-purpose registers and
 * the program counter, refreshed after every step or run.
 *
 * <p>Values are displayed in hex (8 digits, lower-case) which is the
 * convention used by every MIPS lab handout. After each refresh the
 * rows whose values changed since the previous refresh are tagged with
 * the {@code register-row-changed} CSS pseudo-class so the theme can
 * highlight them - matching the behaviour of classic MARS where the
 * just-written register turns yellow.</p>
 *
 * @author Manan
 * @version 2026-04-26
 */
public final class RegistersPane extends BorderPane
{
    /** Backing table model: one {@link Row} per displayed register. */
    private final ObservableList<Row> rows;

    /** Cached PC row reference so we can update it cheaply. */
    private final Row pcRow;

    /** Snapshot of the previous refresh - drives change highlighting. */
    private final int[] previousValues;

    /** Previous program counter, tracked separately. */
    private int previousPc;

    /** Whether {@link #refresh(MarsBackend)} has been called yet. */
    private boolean firstRefresh;

    /**
     * Builds the registers pane with rows for $0..$31 and a final PC row.
     */
    public RegistersPane()
    {
        getStyleClass().add("registers-pane");
        rows = FXCollections.observableArrayList();
        for (int i = 0; i < 32; i++)
        {
            rows.add(new Row(RegisterNames.NAMES[i], i, 0));
        }
        pcRow = new Row("PC", -1, 0);
        rows.add(pcRow);

        previousValues = new int[32];
        previousPc = 0;
        firstRefresh = true;

        TableView<Row> table = new TableView<>(rows);
        table.getStyleClass().add("registers-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setEditable(false);
        table.setRowFactory(tv -> new ChangeAwareRow());

        TableColumn<Row, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(70);

        TableColumn<Row, String> numCol = new TableColumn<>("#");
        numCol.setCellValueFactory(new PropertyValueFactory<>("number"));
        numCol.setPrefWidth(40);

        TableColumn<Row, String> valCol = new TableColumn<>("Value (hex)");
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valCol.setPrefWidth(120);

        table.getColumns().setAll(nameCol, numCol, valCol);

        Label header = new Label("Registers");
        header.getStyleClass().add("pane-header");
        setTop(header);
        setCenter(table);
    }

    /**
     * Reads every register from {@code backend} and refreshes the table.
     * Rows whose value changed since the previous refresh are flagged
     * with the {@code changed} property so the row factory can paint
     * them with a highlight class.
     *
     * @param backend simulator backend to query for register values.
     */
    public void refresh(MarsBackend backend)
    {
        for (int i = 0; i < 32; i++)
        {
            int v = backend.getRegister(i);
            Row r = rows.get(i);
            r.valueProperty().set(formatHex(v));
            // Skip change-flag on the very first refresh so the panel
            // does not light up just because we initialised it.
            r.changedProperty().set(!firstRefresh && v != previousValues[i]);
            previousValues[i] = v;
        }
        int pc = backend.getProgramCounter();
        pcRow.valueProperty().set(formatHex(pc));
        pcRow.changedProperty().set(!firstRefresh && pc != previousPc);
        previousPc = pc;
        firstRefresh = false;
    }

    /**
     * Clears every change flag - useful when assembling fresh code so
     * the panel does not stay lit from the previous run.
     */
    public void clearChangeFlags()
    {
        for (Row r : rows)
        {
            r.changedProperty().set(false);
        }
        firstRefresh = true;
    }

    /**
     * Formats {@code value} as an 8-digit lower-case hex string with
     * the {@code 0x} prefix.
     *
     * @param value 32-bit value to format.
     * @return canonical 10-character hex representation.
     */
    private static String formatHex(int value)
    {
        return String.format("0x%08x", value);
    }

    /**
     * Custom {@link TableRow} that adds/removes the
     * {@code register-row-changed} CSS class as the row's
     * {@code changed} flag flips.
     *
     * <p>JavaFX's {@link TableView} recycles rows aggressively. Every
     * time a row gets rebound to a new model item we must detach the
     * listener we attached to the previous item, otherwise listeners
     * pile up unbounded - a real-world memory leak that froze the UI
     * after a few dozen steps.</p>
     */
    private static final class ChangeAwareRow extends TableRow<Row>
    {
        /** CSS class applied when the row's value just changed. */
        private static final String CLASS = "register-row-changed";

        /** The model item this row is currently bound to, if any. */
        private Row boundItem;

        /** Listener attached to {@link #boundItem}, kept so we can
         *  detach it when the row is rebound to a different item. */
        private javafx.beans.value.ChangeListener<Boolean> changedListener;

        /**
         * Default constructor: wires the row-rebind listener.
         */
        ChangeAwareRow()
        {
            itemProperty().addListener((obs, oldItem, newItem) ->
                bind(newItem));
        }

        /**
         * Detaches any listener from the previous item, then attaches a
         * fresh one to the new item.
         *
         * @param item the new model row, or {@code null} when the
         *             physical row goes off-screen.
         */
        private void bind(Row item)
        {
            // Detach from previous binding to prevent listener leaks.
            if (boundItem != null && changedListener != null)
            {
                boundItem.changedProperty().removeListener(changedListener);
            }
            boundItem = null;
            changedListener = null;

            getStyleClass().removeAll(CLASS);
            if (item == null)
            {
                return;
            }
            if (item.changedProperty().get())
            {
                getStyleClass().add(CLASS);
            }
            changedListener = (obs, was, is) ->
            {
                getStyleClass().removeAll(CLASS);
                if (Boolean.TRUE.equals(is))
                {
                    getStyleClass().add(CLASS);
                }
            };
            item.changedProperty().addListener(changedListener);
            boundItem = item;
        }
    }

    /**
     * Row backing the registers table. Public so JavaFX's
     * {@link PropertyValueFactory} can reach the bean accessors.
     */
    public static final class Row
    {
        /** Conventional register name, e.g. {@code $t0}. */
        private final StringProperty name;

        /** Register number as a string, or empty for the PC row. */
        private final StringProperty number;

        /** Most recent value, formatted as hex. */
        private final StringProperty value;

        /** True when the value just changed since the last refresh. */
        private final BooleanProperty changed;

        /**
         * Builds a row with the given fields.
         *
         * @param nameValue conventional register name.
         * @param numberValue register number, or negative for PC.
         * @param initialValue initial value, formatted by the constructor.
         */
        Row(String nameValue, int numberValue, int initialValue)
        {
            this.name = new SimpleStringProperty(nameValue);
            this.number = new SimpleStringProperty(
                numberValue < 0 ? "" : Integer.toString(numberValue));
            this.value = new SimpleStringProperty(formatHex(initialValue));
            this.changed = new SimpleBooleanProperty(false);
        }

        /**
         * @return the register name property (e.g. {@code $t0}).
         */
        public StringProperty nameProperty()
        {
            return name;
        }

        /**
         * @return the register number property as a string.
         */
        public StringProperty numberProperty()
        {
            return number;
        }

        /**
         * @return the value property, formatted as hex.
         */
        public StringProperty valueProperty()
        {
            return value;
        }

        /**
         * @return the changed-since-last-refresh property.
         */
        public BooleanProperty changedProperty()
        {
            return changed;
        }

        /** @return name string for {@link PropertyValueFactory}. */
        public String getName()
        {
            return name.get();
        }

        /** @return number string for {@link PropertyValueFactory}. */
        public String getNumber()
        {
            return number.get();
        }

        /** @return value string for {@link PropertyValueFactory}. */
        public String getValue()
        {
            return value.get();
        }
    }
}
