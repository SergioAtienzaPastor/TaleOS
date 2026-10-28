package me.kvothe.talecraft.turtle;

final class TurtleBlockIds {

    private TurtleBlockIds() {
    }

    static boolean isTurtleBlockId(String id) {
        return TurtleController.BLOCK_ID.equals(id)
                || id != null && id.startsWith("*TaleCraft_Turtle_State_Definitions_");
    }
}
