package me.kvothe.taleos.turtle;

final class TurtleBlockIds {

    private TurtleBlockIds() {
    }

    static boolean isTurtleBlockId(String id) {
        return TurtleController.BLOCK_ID.equals(id)
                || id != null && id.startsWith("*TaleOS_Turtle_State_Definitions_");
    }
}
