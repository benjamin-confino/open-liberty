/*******************************************************************************
 * Copyright (c) 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.phonehome;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

//This class uses the type system to ensure we don't ever transmit a unique identifier in plaintext
public class HashedString {

    private static final String SALT = "Liberty_Phone_Home";
    private final String hashedString;

    public HashedString(String plainText) throws Exception {

        //adapted from https://stackoverflow.com/a/33085670/54645
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(SALT.getBytes(StandardCharsets.UTF_8));
        byte[] bytes = md.digest(plainText.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
        }
        hashedString = sb.toString();

    }

    @Override
    public String toString() {
        return hashedString;
    }

}
