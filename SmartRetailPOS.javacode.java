import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/** Smart Retail POS - single-file Java Swing app. Run: java SmartRetailPOS.java  (Java 11+) */
public class SmartRetailPOS {

    // ---------- Models ----------
    static class Product {
        int id, qty, reorder, lead; String name, cat, supplier; double price; int[] hist = new int[7];
        Product(int id, String n, String c, int q, int r, String s, double p, int daily, int lead, Random rnd) {
            this.id = id; name = n; cat = c; qty = q; reorder = r; supplier = s; price = p; this.lead = lead;
            for (int i = 0; i < 7; i++) hist[i] = Math.max(0, (int) Math.round(daily * (.6 + rnd.nextDouble() * .8)));
        }
        String code() { return "890100" + (999 + id); }
        double avg() { return Arrays.stream(hist).average().orElse(0); }          // 7-day moving average
        int pred7() { return (int) Math.round(avg() * 7); }
        int safety() { return (int) Math.ceil(avg() * 2); }
        int suggest() { return Math.max(0, (int) Math.ceil(avg() * lead + safety() - qty)); }
        boolean low() { return qty <= reorder; }
        int units() { return Arrays.stream(hist).sum(); }
    }

    static int poSeq = 100230;
    static class PO {
        String id, supplier, status = "Pending"; Map<Product, Integer> items = new LinkedHashMap<>();
        PO(String s) { supplier = s; id = "PO-" + (++poSeq); }
        String desc() { return items.entrySet().stream().map(e -> e.getKey().name + " x" + e.getValue()).collect(Collectors.joining(", ")); }
    }

    static List<Product> products = new ArrayList<>();
    static List<PO> pos = new ArrayList<>();
    static List<Runnable> refreshers = new ArrayList<>();
    static JFrame frame; static String role;
    static final Color BLUE = new Color(37, 99, 235), GREEN = new Color(22, 163, 74), AMBER = new Color(245, 158, 11);

    static String money(double d) { return "\u20B9" + String.format("%,.0f", d); }
    static void reg(Runnable r) { refreshers.add(r); r.run(); }
    static void refreshAll() { refreshers.forEach(Runnable::run); }
    static DefaultTableModel ro(String... cols) {
        return new DefaultTableModel(cols, 0) { public boolean isCellEditable(int r, int c) { return false; } };
    }
    static JPanel pad(JPanel p) { p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); return p; }
    static Font bold(Component c, float s) { return c.getFont().deriveFont(Font.BOLD, s); }

    static void seed() {
        Random rnd = new Random(7);
        Object[][] raw = {
            {"Basmati Rice 5kg","Grocery",12,20,"AgroFresh",520,6,3},{"Sunflower Oil 1L","Grocery",48,25,"AgroFresh",150,5,3},
            {"Whole Wheat Flour 5kg","Grocery",9,15,"AgroFresh",260,4,3},{"Toor Dal 1kg","Grocery",60,20,"AgroFresh",140,4,3},
            {"Full Cream Milk 1L","Dairy",18,30,"DairyBest",60,22,1},{"Salted Butter 500g","Dairy",35,12,"DairyBest",270,3,2},
            {"Paneer 200g","Dairy",14,15,"DairyBest",90,7,1},{"Greek Yogurt 400g","Dairy",40,15,"DairyBest",110,4,2},
            {"Orange Juice 1L","Beverages",55,20,"FreshSip",120,5,2},{"Cola 2L","Beverages",8,25,"FreshSip",95,9,2},
            {"Green Tea 25 bags","Beverages",30,10,"FreshSip",180,2,4},{"Instant Coffee 100g","Beverages",26,12,"FreshSip",310,3,4},
            {"Potato Chips 150g","Snacks",70,30,"CrunchCo",50,12,2},{"Chocolate Bar 55g","Snacks",15,40,"CrunchCo",40,14,2},
            {"Salted Peanuts 200g","Snacks",44,15,"CrunchCo",70,4,2},{"Dish Soap 500ml","Household",33,12,"HomeCare",110,3,5},
            {"Laundry Detergent 1kg","Household",20,10,"HomeCare",230,3,5},{"Toilet Cleaner 500ml","Household",6,10,"HomeCare",95,2,5},
            {"Shampoo 340ml","Personal Care",28,10,"GlowLabs",290,3,4},{"Toothpaste 150g","Personal Care",52,20,"GlowLabs",95,6,4}};
        int i = 1;
        for (Object[] r : raw)
            products.add(new Product(i++, (String) r[0], (String) r[1], (int) r[2], (int) r[3], (String) r[4], (int) r[5], (int) r[6], (int) r[7], rnd));
        PO a = new PO("AgroFresh"); a.items.put(products.get(0), 40); pos.add(a);
        PO b = new PO("FreshSip"); b.items.put(products.get(9), 60); pos.add(b);
    }

    static String[] dayLabels() {
        String[] d = new String[7];
        for (int i = 0; i < 7; i++) d[i] = LocalDate.now().minusDays(6 - i).getDayOfWeek().toString().substring(0, 3);
        return d;
    }
    static double[] daily() {
        double[] d = new double[7];
        for (Product p : products) for (int i = 0; i < 7; i++) d[i] += p.hist[i] * p.price;
        return d;
    }

    // ---------- Simple bar chart (Graphics2D) ----------
    static class BarChart extends JPanel {
        String title; String[] lb = new String[0]; double[] v = new double[0]; Color c;
        BarChart(String t, Color c) { title = t; this.c = c; setPreferredSize(new Dimension(380, 240)); setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY)); }
        void set(String[] l, double[] d) { lb = l; v = d; repaint(); }
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0; g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(g.getFont().deriveFont(Font.BOLD, 13f)); g.drawString(title, 10, 18);
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 10f));
            int n = v.length; if (n == 0) return;
            double max = Arrays.stream(v).max().orElse(1); if (max == 0) max = 1;
            int h = getHeight() - 70, bw = (getWidth() - 30) / n;
            for (int i = 0; i < n; i++) {
                int bh = (int) (v[i] / max * h), x = 15 + i * bw;
                g.setColor(c); g.fillRoundRect(x + 4, 40 + h - bh, bw - 8, bh, 6, 6);
                g.setColor(Color.DARK_GRAY);
                g.drawString(lb[i].length() > 8 ? lb[i].substring(0, 8) : lb[i], x + 4, getHeight() - 10);
                g.drawString(String.valueOf(Math.round(v[i])), x + 4, 36 + h - bh);
            }
        }
    }

    // ---------- Login ----------
    static void showLogin() {
        frame = new JFrame("Smart Retail POS"); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JTextField email = new JTextField("demo@smartretail.com", 18); JPasswordField pw = new JPasswordField("password");
        JComboBox<String> r = new JComboBox<>(new String[]{"Admin", "Manager", "Cashier"});
        JButton go = new JButton("Sign in");
        JPanel p = new JPanel(new GridLayout(0, 1, 8, 8)); p.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        JLabel t = new JLabel("Smart Retail POS", SwingConstants.CENTER); t.setFont(bold(t, 22f));
        p.add(t); p.add(email); p.add(pw); p.add(r); p.add(go);
        p.add(new JLabel("<html><center>Demo: any email/password works.<br>Cashier sees Dashboard + Billing only.</center></html>", SwingConstants.CENTER));
        go.addActionListener(e -> {
            if (email.getText().isBlank() || pw.getPassword().length == 0) { JOptionPane.showMessageDialog(frame, "Enter email and password"); return; }
            role = (String) r.getSelectedItem(); showMain(email.getText());
        });
        frame.setContentPane(p); frame.pack(); frame.setLocationRelativeTo(null); frame.setVisible(true);
    }

    static void showMain(String user) {
        refreshers.clear();
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Dashboard", dashboard()); tabs.add("Billing", billing());
        if (!role.equals("Cashier")) { tabs.add("Inventory", inventory()); tabs.add("AI Reorder", reorder()); tabs.add("Reports", reports()); }
        JPanel top = new JPanel(new BorderLayout(10, 0)); top.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        JLabel bell = new JLabel(); bell.setForeground(Color.RED.darker());
        reg(() -> bell.setText("Low-stock alerts: " + products.stream().filter(Product::low).count()));
        JButton out = new JButton("Sign out"); out.addActionListener(e -> { frame.dispose(); showLogin(); });
        top.add(new JLabel(role + "  |  " + user), BorderLayout.WEST); top.add(bell, BorderLayout.CENTER); top.add(out, BorderLayout.EAST);
        JPanel root = new JPanel(new BorderLayout()); root.add(top, BorderLayout.NORTH); root.add(tabs, BorderLayout.CENTER);
        frame.setContentPane(root); frame.setSize(1150, 720); frame.setLocationRelativeTo(null); frame.revalidate();
    }

    // ---------- Dashboard ----------
    static JPanel dashboard() {
        JPanel p = pad(new JPanel(new BorderLayout(10, 10)));
        String[] names = {"Today's Sales", "Total Products", "Low-Stock Items", "Pending Orders"};
        JLabel[] v = new JLabel[4]; JPanel cards = new JPanel(new GridLayout(1, 4, 10, 10));
        for (int i = 0; i < 4; i++) {
            JPanel c = new JPanel(new BorderLayout()); c.setBorder(BorderFactory.createTitledBorder(names[i]));
            v[i] = new JLabel("", SwingConstants.CENTER); v[i].setFont(bold(v[i], 24f)); c.add(v[i]); cards.add(c);
        }
        BarChart ch = new BarChart("Last 7 days sales (\u20B9)", BLUE);
        JTextArea al = new JTextArea(); al.setEditable(false);
        JScrollPane sp = new JScrollPane(al); sp.setBorder(BorderFactory.createTitledBorder("Low stock alerts")); sp.setPreferredSize(new Dimension(320, 100));
        reg(() -> {
            double[] d = daily();
            v[0].setText(money(d[6])); v[1].setText("" + products.size());
            v[2].setText("" + products.stream().filter(Product::low).count());
            v[3].setText("" + pos.stream().filter(o -> o.status.equals("Pending")).count());
            ch.set(dayLabels(), d);
            al.setText(products.stream().filter(Product::low).map(x -> x.name + " - " + x.qty + " left").collect(Collectors.joining("\n")));
        });
        p.add(cards, BorderLayout.NORTH); p.add(ch, BorderLayout.CENTER); p.add(sp, BorderLayout.EAST);
        return p;
    }

    // ---------- Inventory ----------
    static JPanel inventory() {
        DefaultTableModel m = ro("Code", "Name", "Category", "Qty", "Reorder", "Supplier", "Price", "Status");
        JTable t = new JTable(m); t.setAutoCreateRowSorter(true);
        JTextField q = new JTextField(16);
        Runnable fill = () -> {
            m.setRowCount(0);
            for (Product p : products)
                if ((p.name + p.cat + p.supplier).toLowerCase().contains(q.getText().toLowerCase()))
                    m.addRow(new Object[]{p.code(), p.name, p.cat, p.qty, p.reorder, p.supplier, money(p.price), p.low() ? "LOW" : "In Stock"});
        };
        q.addActionListener(e -> fill.run()); reg(fill);
        java.util.function.Supplier<Product> sel = () -> {
            int r = t.getSelectedRow(); if (r < 0) return null;
            String code = (String) m.getValueAt(t.convertRowIndexToModel(r), 0);
            return products.stream().filter(p -> p.code().equals(code)).findFirst().orElse(null);
        };
        JButton add = new JButton("Add product"), upd = new JButton("Update stock"), del = new JButton("Delete");
        add.addActionListener(e -> {
            JTextField[] f = new JTextField[7]; String[] l = {"Name", "Category", "Qty", "Reorder level", "Supplier", "Price", "Lead days"};
            JPanel fp = new JPanel(new GridLayout(0, 2, 6, 6));
            for (int i = 0; i < 7; i++) { f[i] = new JTextField(); fp.add(new JLabel(l[i])); fp.add(f[i]); }
            if (JOptionPane.showConfirmDialog(frame, fp, "Add product", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            try {
                Product np = new Product(products.size() + 100, f[0].getText(), f[1].getText(), Integer.parseInt(f[2].getText()), Integer.parseInt(f[3].getText()),
                    f[4].getText(), Double.parseDouble(f[5].getText()), 3, Integer.parseInt(f[6].getText()), new Random());
                products.add(np); refreshAll();
            } catch (Exception ex) { JOptionPane.showMessageDialog(frame, "Invalid input: " + ex.getMessage()); }
        });
        upd.addActionListener(e -> {
            Product p = sel.get(); if (p == null) return;
            String s = JOptionPane.showInputDialog(frame, "New quantity for " + p.name, p.qty);
            try { if (s != null) { p.qty = Integer.parseInt(s.trim()); refreshAll(); } } catch (NumberFormatException ex) { JOptionPane.showMessageDialog(frame, "Enter a number"); }
        });
        del.addActionListener(e -> {
            Product p = sel.get();
            if (p != null && JOptionPane.showConfirmDialog(frame, "Delete " + p.name + "?") == JOptionPane.YES_OPTION) { products.remove(p); refreshAll(); }
        });
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT)); bar.add(new JLabel("Search:")); bar.add(q); bar.add(add); bar.add(upd); bar.add(del);
        JPanel p = pad(new JPanel(new BorderLayout(6, 6))); p.add(bar, BorderLayout.NORTH); p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    // ---------- Billing ----------
    static JPanel billing() {
        DefaultTableModel pm = ro("Name", "Price", "Stock"), cm = ro("Item", "Qty", "Price", "Total");
        JTable pt = new JTable(pm), ct = new JTable(cm);
        JTextField q = new JTextField(14); JLabel tot = new JLabel(); tot.setFont(bold(tot, 14f));
        Map<Product, Integer> cart = new LinkedHashMap<>(); List<Product> shown = new ArrayList<>();
        Runnable fillP = () -> {
            pm.setRowCount(0); shown.clear();
            for (Product p : products) if (p.name.toLowerCase().contains(q.getText().toLowerCase())) { shown.add(p); pm.addRow(new Object[]{p.name, money(p.price), p.qty}); }
        };
        Runnable fillC = () -> {
            cart.keySet().retainAll(products); cm.setRowCount(0); double s = 0;
            for (var e : cart.entrySet()) { Product p = e.getKey(); int n = e.getValue(); s += n * p.price; cm.addRow(new Object[]{p.name, n, money(p.price), money(n * p.price)}); }
            tot.setText(String.format("Subtotal %s | GST 5%% %s | TOTAL %s", money(s), money(s * .05), money(s * 1.05)));
        };
        reg(fillP); reg(fillC); q.addActionListener(e -> fillP.run());
        Runnable addSel = () -> {
            int r = pt.getSelectedRow(); if (r < 0) return; Product p = shown.get(r);
            int n = cart.getOrDefault(p, 0);
            if (n + 1 > p.qty) { JOptionPane.showMessageDialog(frame, "Not enough stock"); return; }
            cart.put(p, n + 1); fillC.run();
        };
        pt.addMouseListener(new java.awt.event.MouseAdapter() { public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2) addSel.run(); } });
        JButton add = new JButton("Add to cart"), rem = new JButton("Remove 1"), pay = new JButton("Checkout");
        add.addActionListener(e -> addSel.run());
        rem.addActionListener(e -> {
            int r = ct.getSelectedRow(); if (r < 0) return; Product p = new ArrayList<>(cart.keySet()).get(r);
            if (cart.get(p) <= 1) cart.remove(p); else cart.put(p, cart.get(p) - 1); fillC.run();
        });
        pay.addActionListener(e -> {
            if (cart.isEmpty()) return;
            StringBuilder sb = new StringBuilder("RECEIPT\n-----------------\n"); double s = 0; List<String> newLow = new ArrayList<>();
            for (var en : cart.entrySet()) {
                Product p = en.getKey(); int n = en.getValue(); boolean was = p.low();
                p.qty -= n; p.hist[6] += n; s += n * p.price;
                sb.append(p.name).append(" x").append(n).append("  ").append(money(n * p.price)).append("\n");
                if (!was && p.low()) newLow.add(p.name + " (" + p.qty + " left)");
            }
            sb.append("-----------------\nTotal incl. GST: ").append(money(s * 1.05));
            cart.clear(); refreshAll();
            JOptionPane.showMessageDialog(frame, sb.toString(), "Payment successful", JOptionPane.INFORMATION_MESSAGE);
            if (!newLow.isEmpty()) JOptionPane.showMessageDialog(frame, "Low stock: " + String.join(", ", newLow), "Alert", JOptionPane.WARNING_MESSAGE);
        });
        JPanel left = new JPanel(new BorderLayout(4, 4)); JPanel lt = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lt.add(new JLabel("Search:")); lt.add(q); lt.add(add); left.add(lt, BorderLayout.NORTH); left.add(new JScrollPane(pt));
        left.setBorder(BorderFactory.createTitledBorder("Products (double-click to add)"));
        JPanel right = new JPanel(new BorderLayout(4, 4)); JPanel rb = new JPanel(new FlowLayout(FlowLayout.LEFT)); rb.add(rem); rb.add(pay);
        JPanel south = new JPanel(new GridLayout(2, 1)); south.add(tot); south.add(rb);
        right.add(new JScrollPane(ct)); right.add(south, BorderLayout.SOUTH); right.setBorder(BorderFactory.createTitledBorder("Cart"));
        JPanel p = pad(new JPanel(new GridLayout(1, 2, 10, 10))); p.add(left); p.add(right);
        return p;
    }

    // ---------- AI Reorder ----------
    static JPanel reorder() {
        DefaultTableModel sm = ro("Product", "Current Stock", "Predicted 7-day Demand", "Lead Time", "Suggested Reorder Qty"), om = ro("PO", "Supplier", "Items", "Status");
        JTable ot = new JTable(om);
        reg(() -> {
            sm.setRowCount(0);
            for (Product p : products) if (p.suggest() > 0) sm.addRow(new Object[]{p.name + (p.low() ? "  [LOW]" : ""), p.qty, p.pred7(), p.lead + "d", p.suggest()});
            om.setRowCount(0);
            for (PO o : pos) om.addRow(new Object[]{o.id, o.supplier, o.desc(), o.status});
        });
        JButton ap = new JButton("Approve and Send Purchase Order"), rc = new JButton("Mark selected PO received");
        ap.addActionListener(e -> {
            Map<String, PO> by = new LinkedHashMap<>();
            for (Product p : products) { int s = p.suggest(); if (s > 0) by.computeIfAbsent(p.supplier, PO::new).items.put(p, s); }
            pos.addAll(0, by.values()); refreshAll();
            JOptionPane.showMessageDialog(frame, by.size() + " purchase order(s) sent to suppliers");
        });
        rc.addActionListener(e -> {
            int r = ot.getSelectedRow(); if (r < 0) return; PO o = pos.get(r);
            if (o.status.equals("Pending")) { o.items.forEach((p, n) -> p.qty += n); o.status = "Received"; refreshAll(); }
        });
        JLabel info = new JLabel("<html>Suggested = (avg daily demand [7-day moving avg] x lead days) + safety stock (2 days) - current stock. Predicted demand = avg x 7.</html>");
        JPanel n = new JPanel(new BorderLayout()); n.add(info, BorderLayout.CENTER); n.add(ap, BorderLayout.EAST);
        JPanel top = new JPanel(new BorderLayout(4, 4)); top.add(n, BorderLayout.NORTH); top.add(new JScrollPane(new JTable(sm)));
        JPanel bot = new JPanel(new BorderLayout(4, 4)); bot.setBorder(BorderFactory.createTitledBorder("Purchase Orders"));
        bot.add(new JScrollPane(ot)); bot.add(rc, BorderLayout.SOUTH);
        JPanel p = pad(new JPanel(new GridLayout(2, 1, 10, 10))); p.add(top); p.add(bot);
        return p;
    }

    // ---------- Reports ----------
    static JPanel reports() {
        BarChart a = new BarChart("Sales report (\u20B9)", BLUE), b = new BarChart("Stock by category (units)", GREEN), c = new BarChart("Top-selling products (units, 7 days)", AMBER);
        JLabel st = new JLabel("", SwingConstants.CENTER); st.setFont(bold(st, 15f));
        reg(() -> {
            a.set(dayLabels(), daily());
            Map<String, Integer> cat = new TreeMap<>(); products.forEach(p -> cat.merge(p.cat, p.qty, Integer::sum));
            b.set(cat.keySet().toArray(new String[0]), cat.values().stream().mapToDouble(x -> x).toArray());
            List<Product> top = new ArrayList<>(products); top.sort((x, y) -> y.units() - x.units()); top = top.subList(0, Math.min(8, top.size()));
            c.set(top.stream().map(x -> x.name).toArray(String[]::new), top.stream().mapToDouble(Product::units).toArray());
            st.setText("<html><center>7-day revenue: " + money(Arrays.stream(daily()).sum()) + "<br>Stock value: "
                + money(products.stream().mapToDouble(x -> x.qty * x.price).sum()) + "</center></html>");
        });
        JPanel p = pad(new JPanel(new GridLayout(2, 2, 10, 10))); p.add(a); p.add(b); p.add(c); p.add(st);
        return p;
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
        seed(); SwingUtilities.invokeLater(SmartRetailPOS::showLogin);
    }
}
