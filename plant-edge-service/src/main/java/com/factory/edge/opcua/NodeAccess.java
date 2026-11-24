package com.factory.edge.opcua;

import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;

/** OPC UA CurrentWrite bit (0x02) on AccessLevel and UserAccessLevel. */
final class NodeAccess {

    static final int CURRENT_WRITE = 0x02;

    private NodeAccess() {}

    /**
     * @return {@code null} when either attribute value is missing; otherwise whether both grant CurrentWrite
     */
    static Boolean currentWrite(Object accessLevel, Object userAccessLevel) {
        Integer access = bits(accessLevel);
        Integer user = bits(userAccessLevel);
        if (access == null || user == null) {
            return null;
        }
        return (access & CURRENT_WRITE) != 0 && (user & CURRENT_WRITE) != 0;
    }

    static Integer bits(Object value) {
        if (value instanceof UByte unsigned) {
            return unsigned.intValue();
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
