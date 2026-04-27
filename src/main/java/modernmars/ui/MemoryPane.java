package modernmars.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import modernmars.core.MarsBackend;

/**
 * Memory inspector showing 16 consecutive words starting at a user-
 * specified base address. Addresses are entered in hex (with or without
 * a {@code 0x} prefix); the data segment base ({@code 0x10010000}) is
 * the default since that is where {@code .data} variables live.
 *
 * @author Manan
 * @version 2026-04-26
 */
public final class MemoryPane extends BorderPane
{
    /** Number of consecutive words shown by the table. */
    private static final int WORD_COUNT = 16;

    /** Default base address - MIPS conventional .data segment start. */
    private static final int DEFAULT_BASE = 0x10010000;

    /** Address entry field. */
    private final TextField addressField;

    /** Table backing rows. */
    private final ObservableList<Row> rows;

    /** Most recently used base address, kept in sync with the field. */
    private int baseAddress;

    /**
     * Builds the memory pane with an address bar at the top and a
     * 16-row table beneath.
     */
    public MemoryPane()
    {
        getStyleClass().add("memory-pane");
        baseAddress = DEFAULT_BASE;
        rows = FXCollections.observableArrayList();
        for (int i = 0; i < WORD_COUNT; i++)
        {
            rows.add(new Row(baseAddress + i * 4, 0));
        }

        addressField = new TextField(formatAddress(baseAddress));
        addressField.getStyleClass().add("memory-address-field");
        addressField.setOnAction(e -> applyAddressFromField());
        Label addrLabel = new Label("From:");
        addrLabel.getStyleClass().add("inline-label");
        HBox addrRow = new HBox(6, addrLabel, addressField);
        addrRow.setPadding(new Insets(4, 6, 4, 6));
        HBox.setHgrow(addressField, Priority.ALWAYS);

        TableView<Row> table = new TableView<>(rows);
        table.getStyleClass().add("memory-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Row, String> addrCol = new TableColumn<>("Address");
        addrCol.setCellValueFactory(new PropertyValueFactory<>("address"));
        addrCol.setPrefWidth(120);

        TableColumn<Row, String> valCol = new TableColumn<>("Value (hex)");
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valCol.setPrefWidth(120);

        TableColumn<Row, String> intCol = new TableColumn<>("Int");
        intCol.setCellValueFactory(new PropertyValueFactory<>("intValue"));
        intCol.setPrefWidth(100);

        table.getColumns().setAll(addrCol, valCol, intCol);

        Label header = new Label("Memory");
        header.getStyleClass().add("pane-header");

        BorderPane top = new BorderPane();
        top.setTop(header);
        top.setCenter(addrRow);

        setTop(top);
        setCenter(table);
    }

    /**
     * Refreshes all 16 rows by reading from {@code backend}.
     *
     * @param backend simulator backend providing memory access.
     */
    public void refresh(MarsBackend backend)
    {
        for (int i = 0; i < WORD_COUNT; i++)
        {
            int addr = baseAddress + i * 4;
            int word = backend.readWord(addr);
            Row r = rows.get(i);
            r.addressProperty().set(formatAddress(addr));
            r.valueProperty().set(formatHex(word));
            r.intValueProperty().set(Integer.toString(word));
        }
    }

    /**
     * Parses the contents of the address field and, if valid, updates
     * {@link #baseAddress} and refreshes the row addresses (values will
     * become correct after the next {@link #refresh(MarsBackend)} call).
     */
    private void applyAddressFromField()
    {
        String raw = addressField.getText().trim();
        if (raw.startsWith("0x") || raw.startsWith("0X"))
        {
            raw = raw.substring(2);
        }
        try
        {
            // Use parseUnsignedInt so addresses with the high bit set
            // (e.g. 0x80000000) round-trip cleanly.
            baseAddress = (int) Long.parseLong(raw, 16);
            // Snap to a 4-byte boundary.
            baseAddress &= ~0x3;
            addressField.setText(formatAddress(baseAddress));
            for (int i = 0; i < WORD_COUNT; i++)
            {
                rows.get(i).addressProperty().set(
                    formatAddress(baseAddress + i * 4));
            }
        }
        catch (NumberFormatException nfe)
        {
            // Restore the previous valid address.
            addressField.setText(formatAddress(baseAddress));
        }
    }

    /**
     * Formats an address as {@code 0xXXXXXXXX}.
     *
     * @param address byte address.
     * @return canonical 10-character hex representation.
     */
    private static String formatAddress(int address)
    {
        return String.format("0x%08x", address);
    }

    /**
     * Formats a 32-bit word as {@code 0xXXXXXXXX}.
     *
     * @param value 32-bit value.
     * @return canonical 10-character hex representation.
     */
    private static String formatHex(int value)
    {
        return String.format("0x%08x", value);
    }

    /**
     * Row in the memory table. Public so JavaFX's
     * {@link PropertyValueFactory} can reflect on the accessors.
     */
    public static final class Row
    {
        /** Hex-formatted address of this row's word. */
        private final StringProperty address;

        /** Hex-formatted value at the address. */
        private final StringProperty value;

        /** Decimal int value at the address. */
        private final StringProperty intValue;

        /**
         * Builds a row at {@code addr} with initial value {@code val}.
         *
         * @param addr byte address represented by this row.
         * @param val initial 32-bit value.
         */
        Row(int addr, int val)
        {
            this.address = new SimpleStringProperty(formatAddress(addr));
            this.value = new SimpleStringProperty(formatHex(val));
            this.intValue = new SimpleStringProperty(Integer.toString(val));
        }

        /** @return address property. */
        public StringProperty addressProperty()
        {
            return address;
        }

        /** @return value property. */
        public StringProperty valueProperty()
        {
            return value;
        }

        /** @return decimal int value property. */
        public StringProperty intValueProperty()
        {
            return intValue;
        }

        /** @return address string. */
        public String getAddress()
        {
            return address.get();
        }

        /** @return value string. */
        public String getValue()
        {
            return value.get();
        }

        /** @return integer-as-string. */
        public String getIntValue()
        {
            return intValue.get();
        }
    }
}
