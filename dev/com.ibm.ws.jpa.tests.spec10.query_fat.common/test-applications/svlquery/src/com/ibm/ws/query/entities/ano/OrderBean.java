/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.ws.query.entities.ano;

import java.util.ArrayList;
import java.util.Collection;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.ibm.ws.query.entities.interfaces.ICustomerBean;
import com.ibm.ws.query.entities.interfaces.ILineItem;
import com.ibm.ws.query.entities.interfaces.IOrderBean;

@Entity
@Table(name = "JPAOrderPartTab")
public class OrderBean implements IOrderBean {
    @Id
    private int oid;
    private double amount;
    private boolean delivered;
    @ManyToOne
    private CustomerBean customer;
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "order")
    private Collection<LineItem> lineitems = new ArrayList();

    public OrderBean() {
    }

    public OrderBean(int oid, double amt, boolean delivered, CustomerBean c) {
        this.oid = oid;
        amount = amt;
        this.delivered = delivered;
        customer = c;
        if (c != null)
            c.getOrders().add(this);
    }

    @Override
    public double getAmount() {
        return amount;
    }

    @Override
    public void setAmount(double amount) {
        this.amount = amount;
    }

    @Override
    public CustomerBean getCustomer() {
        return customer;
    }

    @Override
    public void setCustomer(ICustomerBean customer) {
        this.customer = (CustomerBean) customer;
    }

    @Override
    public boolean isDelivered() {
        return delivered;
    }

    @Override
    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    @Override
    public int getOid() {
        return oid;
    }

    @Override
    public void setOid(int oid) {
        this.oid = oid;
    }

    @Override
    public String toString() {
        return "Order:" + oid + " amount:" + amount + " delivered:" + delivered + " customer:" +
               (customer != null ? customer.getId() : -1);
    }

    @Override
    public Collection<LineItem> getLineitems() {
        return lineitems;
    }

    @Override
    public void setLineitems(Collection<? extends ILineItem> lineitems) {
        this.lineitems = (Collection<LineItem>) lineitems;
    }

}
