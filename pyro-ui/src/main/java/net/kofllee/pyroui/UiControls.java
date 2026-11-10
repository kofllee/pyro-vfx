package net.kofllee.pyroui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/** Native-feeling, compact interactive controls; no VFX or Minecraft dependency. */
public final class UiControls {
    private UiControls() {}

    public static final class Label extends UiElement {
        private enum Weight { BODY, HEADING, META }
        private String text;
        private int color = UiTheme.SECONDARY;
        private Weight weight = Weight.BODY;
        private boolean centered;
        public Label(String value) { text = value; height(17); }
        public Label color(int value) { color = value; return this; }
        public Label heading() { weight = Weight.HEADING; color = UiTheme.TEXT; height(19); return this; }
        public Label meta() { weight = Weight.META; height(15); return this; }
        public Label centered() { centered = true; return this; }
        public void set(String value) { text = value; }
        @Override protected void drawSelf(UiCanvas c, UiContext ctx) {
            int available = Math.max(0, bounds.w() - 8);
            int measured = switch (weight) {
                case HEADING -> c.measureHeading(text);
                case META -> c.measureMeta(text);
                default -> c.measure(text);
            };
            int x = bounds.x() + (centered ? Math.max(4, (bounds.w() - measured) / 2) : 4);
            int h = ctx.fontHeight;
            int y = bounds.y() + Math.max(0, (bounds.h() - h) / 2);
            switch (weight) {
                case HEADING -> c.textHeadingClipped(text, x, y, available, color);
                case META -> c.textMetaClipped(text, x, y, available, color);
                default -> c.textClipped(text, x, y, available, color);
            }
        }
    }

    public static class Button extends UiElement {
        private final String label;
        private final Runnable click;
        private boolean pressed, primary, hovered, section, expanded;
        protected boolean compactIcon;
        private long hoverChanged;
        public Button(String label, Runnable action) {
            this.label = label; click = action;
        }
        public Button primary() { primary = true; return this; }
        public Button section() { section = true; return this; }
        public Button section(boolean expanded) { section = true; this.expanded = expanded; return this; }
        @Override protected void drawSelf(UiCanvas c, UiContext ctx) {
            boolean over = bounds.contains(ctx.mouseX, ctx.mouseY);
            if (hovered != over) { hovered = over; hoverChanged = System.nanoTime(); }
            float t = ctx.theme.fade(hoverChanged, 150);
            float hoverAmount = hovered ? t : 1f-t;
            int background = primary ? ctx.theme.accent : section ? UiTheme.PANEL : UiTheme.ELEVATED;
            int hoverColor = primary ? UiTheme.mix(ctx.theme.accent, UiTheme.TEXT, .16f) : UiTheme.HOVER;
            background = UiTheme.mix(background, hoverColor, hoverAmount);
            if (pressed) background = primary ? UiTheme.mix(ctx.theme.accent, UiTheme.BASE, .18f) : 0xFF383838;
            c.rounded(bounds, UiTheme.R_CONTROL, enabled ? background : UiTheme.BASE);
            if (pressed) c.roundedBorder(bounds, UiTheme.R_CONTROL,
                    primary ? UiTheme.HIGHLIGHT : UiTheme.mix(UiTheme.DIVIDER, UiTheme.TEXT, .24f));
            if (section) c.fill(bounds.x()+6,bounds.y()+bounds.h()-1,
                    Math.max(0,bounds.w()-12),1,UiTheme.DIVIDER);
            int color = !enabled ? UiTheme.QUIET : primary ? UiTheme.BG : UiTheme.TEXT;
            if(section && bounds.w()>=22 && bounds.h()>=17)
                c.icon(expanded ? "chevron-down" : "chevron-right",
                        bounds.x()+5,bounds.y()+(bounds.h()-15)/2,15,UiTheme.SECONDARY);
            int available = Math.max(0,bounds.w() - (section ? 34 : 12));
            String shown = label;
            if(c.measure(shown)>available) {
                String end = "…";
                int len=shown.length();
                while(len>0 && c.measure(shown.substring(0,len)+end)>available) len--;
                shown = len==0 ? "" : shown.substring(0,len)+end;
            }
            int textWidth = c.measure(shown);
            int x = section ? bounds.x()+23 :
                    bounds.x()+Math.max(5,(bounds.w()-textWidth)/2);
            c.text(shown, x, bounds.y()+(bounds.h()-ctx.fontHeight)/2,color);
        }
        @Override public boolean onPointerDown(UiContext ctx, double x, double y, int button) {
            if (button != 0) return false;
            pressed = true; return true;
        }
        @Override public void onPointerUp(UiContext ctx, double x, double y, int button) {
            if (pressed && bounds.contains(x, y)) click.run();
            pressed = false;
        }
    }

    /** Square icon button; icon is a bundled Pixelarticons asset, not a character. */
    public static final class IconButton extends Button {
        private final String pixelIcon;
        public IconButton(String icon, Runnable action) {
            super("", action);
            this.pixelIcon=icon;
            width(22);
            height(22);
        }
        @Override protected void drawSelf(UiCanvas c,UiContext ctx) {
            super.drawSelf(c,ctx);
            // Keep a full square inset for the asset, even if the containing
            // flex layout gives this IconButton less than its preferred 22px.
            int side=Math.min(16,Math.min(bounds.w()-6,bounds.h()-6));
            if(side>0) c.icon(pixelIcon,bounds.x()+(bounds.w()-side)/2,
                    bounds.y()+(bounds.h()-side)/2,side,
                    enabled ? UiTheme.TEXT : UiTheme.QUIET);
        }
    }

    public static final class Separator extends UiElement {
        public Separator() { height(9); }
        @Override protected void drawSelf(UiCanvas c, UiContext ctx) {
            c.fill(bounds.x(), bounds.y() + bounds.h() / 2, bounds.w(), 1, UiTheme.DIVIDER);
        }
    }

    public static final class Toggle extends UiElement {
        private final String label;
        private boolean value;
        private final Consumer<Boolean> changed;
        private long animationStarted;
        private boolean previous;
        public Toggle(String label, boolean value, Consumer<Boolean> changed) {
            this.label = label; this.value = value; this.changed = changed;
        }
        public boolean value() { return value; }
        public void set(boolean b) {
            previous = value;
            animationStarted = System.nanoTime();
            value = b;
            changed.accept(value);
        }
        @Override protected void drawSelf(UiCanvas c, UiContext ctx) {
            int w = 29, h = 14;
            UiRect track = new UiRect(bounds.x()+bounds.w()-w-5,
                    bounds.y()+(bounds.h()-h)/2,w,h);
            float ease=ctx.theme.fade(animationStarted,140);
            float current=(previous ? 1f:0f)*(1-ease)+(value?1f:0f)*ease;
            c.rounded(track,UiTheme.R_CONTROL,
                    UiTheme.mix(UiTheme.ELEVATED,ctx.theme.accent,current));
            c.roundedBorder(track,UiTheme.R_CONTROL,
                    value ? ctx.theme.accent : UiTheme.DIVIDER);
            int knobX=track.x()+2+Math.round((w-13)*current);
            c.rounded(new UiRect(knobX,track.y()+2,9,h-4),
                    UiTheme.R_CONTROL,value ? UiTheme.BG : UiTheme.SECONDARY);
            c.textClipped(label,bounds.x()+4,bounds.y()+(bounds.h()-ctx.fontHeight)/2,
                    Math.max(0,bounds.w()-w-17),UiTheme.TEXT);
        }
        @Override public boolean onPointerDown(UiContext ctx, double x, double y, int button) {
            if (button != 0) return false;
            set(!value); return true;
        }
    }

    public static final class Check extends UiElement {
        private final String label;
        private boolean checked;
        private final Consumer<Boolean> changed;
        public Check(String label, boolean initial, Consumer<Boolean> changed) {
            this.label = label; checked = initial; this.changed = changed;
        }
        @Override protected void drawSelf(UiCanvas c, UiContext ctx) {
            UiRect box = new UiRect(bounds.x()+3,bounds.y()+(bounds.h()-16)/2,16,16);
            c.rounded(box, UiTheme.R_CONTROL, checked ? ctx.theme.accent : UiTheme.ELEVATED);
            if (!checked) c.roundedBorder(box,UiTheme.R_CONTROL,UiTheme.DIVIDER);
            else if(bounds.w()>=23 && bounds.h()>=16)
                c.icon("check",box.x(),box.y(),15,UiTheme.BG);
            c.textClipped(label,bounds.x()+26,
                    bounds.y()+(bounds.h()-ctx.fontHeight)/2,
                    Math.max(0,bounds.w()-28),UiTheme.TEXT);
        }
        @Override public boolean onPointerDown(UiContext ctx, double x, double y, int button) {
            if (button != 0) return false;
            changed.accept(checked = !checked); return true;
        }
    }

    /** Single line editing uses the host font metrics and the host clipboard. */
    public static class TextField extends UiElement {
        protected String text;
        protected int caret, anchor, offset;
        protected final Consumer<String> changed;
        private boolean selecting;
        public TextField(String initial, Consumer<String> changed) {
            text = initial == null ? "" : initial;
            caret = anchor = text.length();
            this.changed = changed;
        }
        public String value() { return text; }
        public boolean hasSelection() { return caret != anchor; }
        public int selectionStart() { return Math.min(caret, anchor); }
        public int selectionEnd() { return Math.max(caret, anchor); }
        public int caret() { return caret; }
        public void setText(String value) {
            text = value == null ? "" : value;
            caret = anchor = Math.min(caret, text.length());
        }
        public void selectAll() { anchor = 0; caret = text.length(); }
        @Override public boolean focusable() { return true; }
        protected int textLeftPadding() { return 6; }
        private int textWidth(UiContext ctx, String s) { return ctx.measure.applyAsInt(s); }
        protected void ensureCaretVisible(UiContext ctx) {
            int max = Math.max(1, bounds.w() - textLeftPadding() - 6);
            int position = textWidth(ctx, text.substring(0, caret));
            if (position - offset > max) offset = position - max;
            if (position - offset < 0) offset = position;
            offset = Math.max(0, offset);
        }
        private int positionAt(UiContext ctx, double x) {
            double target = x - bounds.x() - textLeftPadding() + offset;
            int nearest = 0;
            for (int i = 1; i <= text.length(); i++) {
                int a = textWidth(ctx, text.substring(0, i - 1));
                int b = textWidth(ctx, text.substring(0, i));
                if (target < (a + b) / 2.0) break;
                nearest = i;
            }
            return nearest;
        }
        @Override protected void drawSelf(UiCanvas c, UiContext ctx) {
            boolean focused = ctx.focus == this;
            c.rounded(bounds, UiTheme.R_CONTROL, focused ? 0xFF171312 : UiTheme.BASE);
            c.roundedBorder(bounds, UiTheme.R_CONTROL,
                    focused ? ctx.theme.accent : UiTheme.DIVIDER);
            if (focused) ensureCaretVisible(ctx);
            else offset = 0;
            c.clip(bounds.inset(2));
            int drawX = bounds.x() + textLeftPadding() - offset;
            int baseline = bounds.y() + (bounds.h() - ctx.fontHeight) / 2;
            if (focused && hasSelection()) {
                int x1 = drawX + textWidth(ctx, text.substring(0, selectionStart()));
                int x2 = drawX + textWidth(ctx, text.substring(0, selectionEnd()));
                c.fill(x1, baseline - 2, Math.max(1, x2 - x1), ctx.fontHeight + 4,
                        UiTheme.alpha(ctx.theme.accent, .40f));
            }
            c.text(text, drawX, baseline, UiTheme.TEXT);
            if (focused && System.currentTimeMillis() % 1000 < 580) {
                int cx = drawX + textWidth(ctx, text.substring(0, caret));
                c.fill(cx, baseline - 2, 1, ctx.fontHeight + 4, ctx.theme.accent);
            }
            c.unclip();
        }
        @Override public boolean onPointerDown(UiContext ctx, double x, double y, int button) {
            if (button != 0) return false;
            ctx.setFocus(this);
            caret = positionAt(ctx, x);
            anchor = caret;
            selecting = true;
            ensureCaretVisible(ctx);
            return true;
        }
        @Override public void onPointerMove(UiContext ctx, double x, double y) {
            if (selecting) {
                caret = positionAt(ctx, x);
                ensureCaretVisible(ctx);
            }
        }
        @Override public void onPointerUp(UiContext ctx, double x, double y, int button) {
            if (selecting) caret = positionAt(ctx, x);
            selecting = false;
        }
        @Override public void blur(UiContext ctx) { selecting = false; offset = 0; }
        protected void replace(int from, int to, String insertion) {
            if (from < 0 || to > text.length() || from > to) return;
            if (text.length() + insertion.length() - (to - from) > 256) return;
            text = text.substring(0, from) + insertion + text.substring(to);
            caret = anchor = from + insertion.length();
            changed.accept(text);
        }
        private void replaceSelection(String replacement) {
            replace(selectionStart(), selectionEnd(), replacement);
        }
        private void move(int to, boolean selecting) {
            caret = Math.max(0, Math.min(text.length(), to));
            if (!selecting) anchor = caret;
        }
        @Override public boolean onKey(UiContext ctx, int key, int mods) {
            boolean ctrl = (mods & 2) != 0, shift = (mods & 1) != 0;
            if (ctrl) {
                if (key == 65) { selectAll(); return true; } // Ctrl-A
                if (key == 67) {
                    if (hasSelection()) ctx.writeClipboard.accept(text.substring(selectionStart(), selectionEnd()));
                    return true;
                }
                if (key == 88) {
                    if (hasSelection()) {
                        ctx.writeClipboard.accept(text.substring(selectionStart(), selectionEnd()));
                        replaceSelection("");
                    }
                    return true;
                }
                if (key == 86) {
                    String clip = ctx.readClipboard.get();
                    if (clip != null) replaceSelection(clip.replaceAll("[\\r\\n\\t]", " "));
                    return true;
                }
            }
            if (key == 259) {
                if (hasSelection()) replaceSelection("");
                else if (caret > 0) replace(Character.offsetByCodePoints(text, caret, -1), caret, "");
            } else if (key == 261) {
                if (hasSelection()) replaceSelection("");
                else if (caret < text.length()) replace(caret,
                        Character.offsetByCodePoints(text, caret, 1), "");
            } else if (key == 263) move(ctrl ? wordLeft(caret) :
                    (caret > 0 ? Character.offsetByCodePoints(text, caret, -1) : caret), shift);
            else if (key == 262) move(ctrl ? wordRight(caret) :
                    (caret < text.length() ? Character.offsetByCodePoints(text, caret, 1) : caret), shift);
            else if (key == 268) move(0, shift);
            else if (key == 269) move(text.length(), shift);
            else if (key == 257 || key == 335 || key == 256) { ctx.setFocus(null); return true; }
            else return false;
            ensureCaretVisible(ctx);
            return true;
        }
        private int wordLeft(int at) {
            while (at > 0 && Character.isWhitespace(text.charAt(at - 1))) at--;
            while (at > 0 && !Character.isWhitespace(text.charAt(at - 1))) at--;
            return at;
        }
        private int wordRight(int at) {
            while (at < text.length() && !Character.isWhitespace(text.charAt(at))) at++;
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++;
            return at;
        }
        @Override public boolean onChar(UiContext ctx, char c) {
            if (c < 32 || c == 127) return false;
            replaceSelection(String.valueOf(c));
            ensureCaretVisible(ctx);
            return true;
        }
    }

    public static final class NumberField extends TextField {
        private final double min, max, step;
        private final DoubleConsumer applied;
        private double number;
        private boolean dragging;
        private double dragOrigin, initialValue;
        public NumberField(double initial, double min, double max, double step, DoubleConsumer applied) {
            super(format(initial), s -> {});
            this.min = min; this.max = max; this.step = step;
            this.applied = applied; number = initial;
        }
        private static String format(double v) { return String.format(Locale.ROOT, "%.2f", v); }
        public double numberValue() { return number; }
        public void setNumber(double v) {
            if (!Double.isFinite(v)) return;
            number = Math.max(min, Math.min(max, v));
            setText(format(number));
        }
        @Override protected int textLeftPadding() { return 24; }
        @Override protected void replace(int from, int to, String insertion) {
            super.replace(from, to, insertion);
            try {
                double candidate = Double.parseDouble(text);
                if (Double.isFinite(candidate)) {
                    number = Math.max(min, Math.min(max, candidate));
                    applied.accept(number);
                }
            } catch (NumberFormatException ignored) { /* unfinished - or decimal stays editable */ }
        }
        @Override public boolean onPointerDown(UiContext ctx, double x, double y, int button) {
            if (button != 0) return false;
            if (x <= bounds.x() + 21) {
                dragging = true; dragOrigin = x; initialValue = number;
                ctx.setFocus(null); return true;
            }
            return super.onPointerDown(ctx, x, y, button);
        }
        @Override public void onPointerMove(UiContext ctx, double x, double y) {
            if (dragging) {
                setNumber(initialValue + Math.round((x - dragOrigin) / 7.0) * step);
                applied.accept(number);
            } else super.onPointerMove(ctx, x, y);
        }
        @Override public void onPointerUp(UiContext ctx, double x, double y, int button) {
            dragging = false;
            super.onPointerUp(ctx, x, y, button);
        }
        @Override protected void drawSelf(UiCanvas c, UiContext ctx) {
            super.drawSelf(c, ctx);
            c.rounded(new UiRect(bounds.x()+1,bounds.y()+1,
                    Math.max(0,Math.min(21,bounds.w()-2)),
                    Math.max(0,bounds.h()-2)),
                    UiTheme.R_CONTROL,UiTheme.ELEVATED);
            // Use the same 1.25x Mojangles glyph scale as other icons.
            // A 15px icon belongs to a 21px grip, without independent
            // shrinking that would create a second pixel density.
            if(bounds.w()>=23 && bounds.h()>=17)
                c.icon("move",bounds.x()+4,
                        bounds.y()+(bounds.h()-15)/2,15,UiTheme.SECONDARY);
        }
        @Override public void blur(UiContext ctx) {
            super.blur(ctx);
            setNumber(number);
        }
    }

    public static final class Slider extends UiElement {
        private double value;
        private final double min, max;
        private final DoubleConsumer changed;
        private boolean dragging;
        public Slider(double initial, double min, double max, DoubleConsumer changed) {
            value = initial; this.min = min; this.max = max; this.changed = changed;
        }
        public double numberValue() { return value; }
        public void setNumber(double v) {
            if (Double.isFinite(v)) value = Math.max(min, Math.min(max, v));
        }
        @Override protected void drawSelf(UiCanvas c, UiContext ctx) {
            int x=bounds.x()+5,w=Math.max(1,bounds.w()-10);
            int y=bounds.y()+bounds.h()/2;
            c.fill(x,y-1,w,2,UiTheme.HOVER);
            int position=UiRect.clamp((int)Math.round((w-1)*(value-min)/
                    Math.max(0.00001,max-min)),0,w-1);
            c.fill(x,y-1,position+1,2,ctx.theme.accent);
            boolean over=dragging||bounds.contains(ctx.mouseX,ctx.mouseY);
            c.rounded(new UiRect(x+position-3,y-(over?7:6),
                    7,over?14:12),UiTheme.R_CONTROL,
                    over?UiTheme.TEXT:0xFFD1D1D1);
            c.fill(x+position,y-4,1,8,ctx.theme.accent);
        }
        private void update(double x) {
            value = min + Math.max(0, Math.min(1,
                    (x - bounds.x() - 4) / Math.max(1, bounds.w() - 8))) * (max - min);
            changed.accept(value);
        }
        @Override public boolean onPointerDown(UiContext ctx, double x, double y, int button) {
            if (button != 0) return false;
            dragging = true; update(x); return true;
        }
        @Override public void onPointerMove(UiContext ctx, double x, double y) {
            if (dragging) update(x);
        }
        @Override public void onPointerUp(UiContext ctx, double x, double y, int button) { dragging = false; }
        @Override public boolean onScroll(UiContext ctx, double amount) {
            value = Math.max(min, Math.min(max, value + amount * (max - min) / 30));
            changed.accept(value); return true;
        }
    }

    public static final class Dropdown extends UiElement {
        private final List<String> values;
        private int selected;
        private final IntConsumer changed;
        private boolean hovered;
        private long hoverChangedAt;
        public Dropdown(List<String> values, int selected, IntConsumer changed) {
            this.values = List.copyOf(values); this.selected = selected; this.changed = changed;
        }
        @Override protected void drawSelf(UiCanvas c, UiContext ctx) {
            boolean over = bounds.contains(ctx.mouseX, ctx.mouseY);
            if (hovered != over) { hovered = over; hoverChangedAt = System.nanoTime(); }
            float amount = hovered ? ctx.theme.fade(hoverChangedAt, 145) :
                    1f - ctx.theme.fade(hoverChangedAt, 145);
            c.rounded(bounds, UiTheme.R_CONTROL,
                    UiTheme.mix(UiTheme.ELEVATED, UiTheme.HOVER, amount));
            if (ctx.popupVisible()) c.roundedBorder(bounds, UiTheme.R_CONTROL, ctx.theme.accent);
            if (!values.isEmpty()) c.textClipped(values.get(UiRect.clamp(selected, 0, values.size() - 1)),
                    bounds.x() + 9, bounds.y() + (bounds.h() - ctx.fontHeight) / 2,
                    Math.max(0,bounds.w() - 36), UiTheme.TEXT);
            if(bounds.w()>=25 && bounds.h()>=17)
                c.icon("chevron-down",bounds.x()+bounds.w()-21,
                        bounds.y()+(bounds.h()-15)/2,15,UiTheme.SECONDARY);
        }
        @Override public boolean onPointerDown(UiContext ctx, double x, double y, int button) {
            if (button != 0) return false;
            ctx.menu(bounds, values, i -> { selected = i; changed.accept(i); });
            return true;
        }
    }

    public static final class Swatch extends UiElement {
        private int color;
        private final Consumer<Integer> changed;
        private static final List<Integer> COLORS = List.of(
                0xFFFF6432, 0xFFFFB067, 0xFF73C9C6, 0xFFA998EF, 0xFFE96D75, 0xFFFFFFFF);
        public Swatch(int initial, Consumer<Integer> changed) { color = initial; this.changed = changed; }
        @Override protected void drawSelf(UiCanvas c, UiContext ctx) {
            boolean over = bounds.contains(ctx.mouseX, ctx.mouseY);
            c.rounded(bounds, UiTheme.R_CONTROL, over ? UiTheme.HOVER : UiTheme.ELEVATED);
            c.rounded(new UiRect(bounds.x() + 6, bounds.y() + 4, 18,
                    Math.max(0, bounds.h() - 8)), UiTheme.R_CONTROL, color);
            c.textClipped(String.format("#%06X", color & 0xffffff), bounds.x() + 31,
                    bounds.y() + (bounds.h() - ctx.fontHeight) / 2,
                    Math.max(0,bounds.w() - 57), UiTheme.TEXT);
            if(bounds.w()>=25 && bounds.h()>=17)
                c.icon("chevron-down",bounds.x()+bounds.w()-21,
                        bounds.y()+(bounds.h()-15)/2,15,UiTheme.SECONDARY);
        }
        @Override public boolean onPointerDown(UiContext ctx, double x, double y, int button) {
            if (button != 0) return false;
            ctx.menu(bounds, COLORS.stream().map(i -> String.format("#%06X", i & 0xffffff)).toList(), i -> {
                color = COLORS.get(i); changed.accept(color);
            });
            return true;
        }
    }

    public record TreeEntry(String id, String name, int depth, boolean folder) {}
    public static final class Tree extends UiElement {
        private final List<TreeEntry> entries;
        private final java.util.Set<String> collapsed = new java.util.HashSet<>();
        private final Consumer<String> selected;
        private String selection;
        private int lineScroll;
        private int rowHeight = 23;
        private String hoveredId = "";
        private long hoverChangedAt, selectedAt;
        public Tree(List<TreeEntry> entries, String initial, Consumer<String> selected) {
            this.entries = new ArrayList<>(entries); this.selection = initial;
            this.selected = selected;
        }
        @Override protected void drawSelf(UiCanvas c, UiContext ctx) {
            c.fill(bounds, UiTheme.PANEL);
            c.clip(bounds);
            int y = bounds.y() + 4 - lineScroll;
            for (var entry : entries) {
                if (hidden(entry)) continue;
                UiRect row = new UiRect(bounds.x() + 2, y,
                        Math.max(0, bounds.w() - 4), rowHeight - 1);
                if (row.y() + row.h() > bounds.y() && row.y() < bounds.y() + bounds.h()) {
                    boolean isSelected = entry.id().equals(selection);
                    boolean over = row.contains(ctx.mouseX, ctx.mouseY);
                    if (over && !entry.id().equals(hoveredId)) {
                        hoveredId = entry.id();
                        hoverChangedAt = System.nanoTime();
                    }
                    if (isSelected) {
                        float alpha = ctx.theme.fade(selectedAt, 170);
                        c.rounded(row, UiTheme.R_CONTROL,
                                UiTheme.mix(UiTheme.PANEL, ctx.theme.accent, .14f * alpha));
                        c.gradientHorizontal(new UiRect(row.x()+3, row.y()+2,
                                Math.min(28, row.w()-6), Math.max(0, row.h()-4)),
                                UiTheme.alpha(ctx.theme.accent, .18f), 0x00181818);
                        c.rounded(new UiRect(row.x()+2, row.y()+4, 2,
                                Math.max(1,row.h()-8)), 1, ctx.theme.accent);
                    } else if (over) {
                        c.rounded(row, UiTheme.R_CONTROL,
                                UiTheme.mix(UiTheme.PANEL, UiTheme.HOVER,
                                        ctx.theme.fade(hoverChangedAt, 140)));
                    }
                    int at = row.x() + 9 + entry.depth() * 13;
                    if (entry.folder()) {
                        int cy = row.y() + row.h()/2;
                        int col = isSelected ? UiTheme.HIGHLIGHT : UiTheme.SECONDARY;
                        if(at+14<=row.x()+row.w() && at>=row.x()+2)
                            c.icon(collapsed.contains(entry.id())?
                                    "chevron-right":"chevron-down",at-2,cy-8,16,col);
                    } else if (entry.depth() > 0) {
                        c.fill(at+3,row.y()+row.h()/2-1,3,3,
                                isSelected?ctx.theme.accent:UiTheme.QUIET);
                    }
                    c.textClipped(entry.name(), at + 12,
                            row.y()+(row.h()-ctx.fontHeight)/2,
                            row.w()-(at-row.x())-18,
                            isSelected ? UiTheme.TEXT : over ? UiTheme.TEXT : UiTheme.SECONDARY);
                }
                y += rowHeight;
            }
            long visibleCount = entries.stream().filter(e -> !hidden(e)).count();
            int content = (int)visibleCount * rowHeight + 8;
            if (content > bounds.h() && bounds.h() > 24) {
                int track = bounds.h()-12;
                int thumb = UiRect.clamp((int)((long)track*bounds.h()/content), 20, track);
                int top = bounds.y()+6 + (int)((long)(track-thumb)*lineScroll/
                        Math.max(1,content-bounds.h()));
                c.rounded(new UiRect(bounds.x()+bounds.w()-5,bounds.y()+6,3,track),
                        2,0xFF282828);
                c.rounded(new UiRect(bounds.x()+bounds.w()-5,top,3,thumb),
                        2,UiTheme.HOVER);
            }
            c.unclip();
        }
        public void rename(String id, String label) {
            for (int i = 0; i < entries.size(); i++) {
                TreeEntry item = entries.get(i);
                if (item.id().equals(id)) {
                    entries.set(i, new TreeEntry(item.id(), label, item.depth(), item.folder()));
                    break;
                }
            }
        }
        private boolean hidden(TreeEntry entry) {
            for (String id : collapsed) if (entry.id().startsWith(id + "/")) return true;
            return false;
        }
        private TreeEntry at(double y) {
            int idx = (int) ((y - bounds.y() - 4 + lineScroll) / rowHeight);
            for (var entry : entries) {
                if (hidden(entry)) continue;
                if (idx-- == 0) return entry;
            }
            return null;
        }
        @Override public boolean onPointerDown(UiContext ctx, double x, double y, int button) {
            TreeEntry entry = at(y);
            if (entry == null) return false;
            if (button == 1) {
                ctx.menu(new UiRect((int) x, (int) y - 20, 145, 20),
                        List.of("Select", "Toggle group"), n -> {
                            if (n == 0) select(entry);
                            if (n == 1 && entry.folder()) toggle(entry);
                        });
                return true;
            }
            if (button != 0) return false;
            if (entry.folder() && x - bounds.x() < 23 + entry.depth() * 15) toggle(entry);
            else select(entry);
            return true;
        }
        private void toggle(TreeEntry entry) {
            if (!collapsed.add(entry.id())) collapsed.remove(entry.id());
            lineScroll = 0;
        }
        private void select(TreeEntry entry) {
            selection = entry.id();
            selectedAt = System.nanoTime();
            selected.accept(selection);
        }
        @Override public boolean onScroll(UiContext ctx, double amount) {
            long visible = entries.stream().filter(e -> !hidden(e)).count();
            lineScroll = UiRect.clamp(lineScroll - (int) amount * rowHeight, 0,
                    Math.max(0, (int) visible * rowHeight - bounds.h() + 6));
            return true;
        }
    }

    public record Point(double time, double value) {}
    public enum Interpolation { LINEAR, STEP, CATMULL, BEZIER }

    /** Matches Pyro VFX CurveDefinition.sample: Bezier is smoothstep in runtime. */
    public static final class Curve extends UiElement {
        private final ArrayList<Point> points = new ArrayList<>(List.of(
                new Point(0, .05), new Point(.18, .82), new Point(.55, .38), new Point(1, 1)));
        private final Runnable changed;
        private int active = -1, selected = -1;
        private Interpolation interpolation = Interpolation.LINEAR;
        public Curve(Runnable changed) { this.changed = changed; }
        public Interpolation interpolation() { return interpolation; }
        public void interpolation(Interpolation value) { interpolation = value; changed.run(); }
        public void setPoints(List<Point> incoming) {
            if (incoming == null || incoming.isEmpty()) throw new IllegalArgumentException("No curve points");
            points.clear();
            points.addAll(incoming.stream().sorted(java.util.Comparator.comparingDouble(Point::time)).toList());
            changed.run();
        }
        public List<Point> points() { return List.copyOf(points); }
        public double sample(double x) {
            if (points.size() == 1 || x <= points.getFirst().time()) return points.getFirst().value();
            if (x >= points.getLast().time()) return points.getLast().value();
            int right = 1;
            while (right < points.size() - 1 && x > points.get(right).time()) right++;
            int left = right - 1;
            Point a = points.get(left), b = points.get(right);
            double span = b.time() - a.time();
            double t = span == 0 ? 0 : (x - a.time()) / span;
            return switch (interpolation) {
                case STEP -> a.value();
                case LINEAR -> lerp(a.value(), b.value(), t);
                case BEZIER -> lerp(a.value(), b.value(), t*t*(3 - 2*t));
                case CATMULL -> {
                    double p0 = points.get(Math.max(0, left - 1)).value();
                    double p1 = a.value(), p2 = b.value();
                    double p3 = points.get(Math.min(points.size() - 1, right + 1)).value();
                    yield .5 * (2*p1 + (-p0+p2)*t
                            + (2*p0-5*p1+4*p2-p3)*t*t
                            + (-p0+3*p1-3*p2+p3)*t*t*t);
                }
            };
        }
        private static double lerp(double a, double b, double t) { return a + (b-a)*t; }
        private UiRect graph() {
            return new UiRect(bounds.x() + 29, bounds.y() + 9,
                    Math.max(1, bounds.w() - 47), Math.max(1, bounds.h() - 30));
        }
        @Override protected void drawSelf(UiCanvas c, UiContext ctx) {
            c.rounded(bounds, UiTheme.R_CONTROL, UiTheme.BASE);
            UiRect g = graph();
            c.rounded(new UiRect(g.x()-3,g.y()-3,g.w()+6,g.h()+6),UiTheme.R_CONTROL,UiTheme.BG);
            c.clip(g);
            c.gradientStops(new UiRect(g.x(),g.y(),g.w(),Math.max(0,g.h()/2)),
                    0x241D130F,0x10130E0C,0x00000000);
            for (int i = 0; i <= 10; i++) {
                int x = g.x() + i * g.w() / 10;
                c.fill(x, g.y(), 1, g.h(), i % 5 == 0 ? 0xFF393939 : 0xFF222222);
            }
            for (int i = 0; i <= 5; i++) {
                int y = g.y() + i * g.h() / 5;
                c.fill(g.x(), y, g.w(), 1, i % 5 == 0 ? 0xFF39414B : 0xFF23272E);
            }
            // Dense sampling follows the runtime's piecewise interpolation, including STEP.
            // A sampled line needs at most ~one point per 3 GUI pixels:
            // UiCanvas.line already resolves the intervening anti-aliased pixels.
            // Previously we sampled at 2x every pixel and drew a second shadow
            // pass, producing tens of thousands of quads on every frame.
            int samples = Math.max(2, Math.min(ctx.lowDetail ? 100 : 380,
                    g.w() / (ctx.lowDetail ? 7 : 3)));
            double prevX = g.x(), prevY = y(g, sample(0));
            for (int i = 1; i <= samples; i++) {
                double t = i / (double) samples;
                double x = g.x() + t * g.w();
                double y = y(g, sample(t));
                if (!ctx.lowDetail)
                    c.line(prevX, prevY + 1, x, y + 1,
                            UiTheme.alpha(ctx.theme.accent,.17f), 1);
                c.line(prevX, prevY, x, y, ctx.theme.accent, 1);
                prevX = x; prevY = y;
            }
            for (int i = 0; i < points.size(); i++) {
                Point p = points.get(i);
                int x = (int) Math.round(g.x() + g.w()*p.time());
                int y = (int) Math.round(y(g, p.value()));
                c.rounded(new UiRect(x-5,y-5,11,11),1,UiTheme.BASE);
                c.rounded(new UiRect(x-3,y-3,7,7),1,
                        i==selected?UiTheme.HIGHLIGHT:ctx.theme.accent);
                if (i==selected) c.roundedBorder(new UiRect(x-5,y-5,11,11),1,
                        UiTheme.alpha(ctx.theme.accent,.7f));
            }
            c.unclip();
            c.textMeta("1.0", bounds.x() + 5, g.y(), UiTheme.SECONDARY);
            c.textMeta("0.0", bounds.x() + 5, g.y()+g.h()-8, UiTheme.SECONDARY);
            c.textMeta("0", g.x(), g.y()+g.h()+7, UiTheme.SECONDARY);
            c.textMeta("1", g.x()+g.w()-5, g.y()+g.h()+7, UiTheme.SECONDARY);
        }
        private double y(UiRect r, double v) { return r.y() + (1-v)*r.h(); }
        @Override public boolean onPointerDown(UiContext ctx, double x, double y, int button) {
            if (button != 0 || !graph().contains(x, y)) return false;
            UiRect g = graph();
            for (int i = 0; i < points.size(); i++) {
                Point p = points.get(i);
                if (Math.hypot(g.x()+g.w()*p.time()-x, y(g,p.value())-y) <= 10) {
                    active = selected = i; return true;
                }
            }
            return true;
        }
        @Override public void onPointerMove(UiContext ctx, double x, double y) {
            if (active < 0) return;
            UiRect g = graph();
            double t = Math.max(0, Math.min(1, (x-g.x())/Math.max(1,g.w())));
            double v = Math.max(0, Math.min(1, 1-(y-g.y())/Math.max(1,g.h())));
            double lo = active == 0 ? 0 : points.get(active-1).time()+.01;
            double hi = active == points.size()-1 ? 1 : points.get(active+1).time()-.01;
            points.set(active, new Point(Math.max(lo,Math.min(hi,t)),v));
            changed.run();
        }
        @Override public void onPointerUp(UiContext ctx, double x, double y, int button) { active = -1; }
    }
}
