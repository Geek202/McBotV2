package me.geek.tom.mcbot.mappings;

@SuppressWarnings("unused")
public class Mapping {

    private final String intermediate;
    private final String notchName;

    private String name;
    private String owner;

    private final String desc;

    private final int id;
    private final Type type;

    public Mapping(String intermediate, String notchName, String name, int id, Type type) {
        this(intermediate, notchName, name, "", "", id, type);
    }

    public Mapping(String intermediate, String notchName, String name, String owner, String desc, int id, Type type) {
        this.intermediate = intermediate;
        this.notchName = notchName;
        this.name = name;
        this.owner = owner;
        this.desc = desc;
        this.id = id;
        this.type = type;
    }

    public String getIntermediate() {
        return intermediate.isEmpty() ? name : intermediate;
    }
    public String getNotchName() {
        return notchName;
    }
    public String getName() {
        return name;
    }
    public int getId() {
        return id;
    }
    public Type getType() {
        return type;
    }
    public String getOwner() {
        return owner;
    }

    public String toAccessWidener() {
        switch (type) {
            case CLASS:
                return "<access> class " + name;
            case FIELD:
                return "<access> field " + owner + " " + name + " " + desc;
            case METHOD:
                return "<access> method " + owner + " " + name + " " + desc;
            default:
                return "";
        }
    }

    public String toAccessTransformer() {
        switch (type) {
            case CLASS:
                return "<access> " + name.replace("/", ".");
            case FIELD:
                return "<access> " + owner.replace("/", ".") + " " + intermediate + " # " + name;
            case METHOD:
                return "<access> " + owner.replace("/", ".") + " " + intermediate + desc + " # " + name;
            default:
                return "";
        }
    }

    private String toNamesString() {
        return notchName + " -> " + intermediate + " -> " + name;
    }

    public String asDiscordString() {
        switch (type) {
            case CLASS:
                return "**__Class: __**`" + toNamesString() + "`\n" +
                        "**__Access widener (Fabric): __**`" + toAccessWidener() + "`\n" +
                        "**__Access transformer (Forge): __**`" + toAccessTransformer() + "`\n";
            case FIELD:
                return "**__Owner: __**`" + owner + "`\n" +
                        "**__Field: __**`" + toNamesString() + "`\n" +
                        "**__Access widener (Fabric): __**`" + toAccessWidener() + "`\n" +
                        "**__Access transformer (Forge): __**`" + toAccessTransformer() + "`\n";
            case METHOD:
                return "**__Owner: __**`" + owner + "`\n" +
                        "**__Method: __**`" + toNamesString() + "`\n" +
                        "**__Descriptor: __**`" + desc + "`\n" +
                        "**__Access widener (Fabric): __**`" + toAccessWidener() + "`\n" +
                        "**__Access transformer (Forge): __**`" + toAccessTransformer() + "`\n";
            default:
                return "";
        }
    }

    public static int getIntermediaryId(String intermediary) {
        String[] pts = intermediary.split("_");
        if (pts.length > 1) {
            try {
                return Integer.parseInt(pts[1]);
            } catch (NumberFormatException ignored) { }
        }
        try {
            return Integer.parseInt(pts[0]);
        } catch (NumberFormatException ignored) { }
        return -1;
    }

    public boolean matchesSearch(String query) {
        return getName().endsWith(query) || getIntermediate().endsWith(query);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public enum Type {
        CLASS, FIELD, METHOD
    }
}
