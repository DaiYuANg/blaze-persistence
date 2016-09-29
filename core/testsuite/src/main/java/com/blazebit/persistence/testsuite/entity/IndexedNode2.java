package com.blazebit.persistence.testsuite.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

/**
 *
 * @author Moritz Becker
 * @since 1.2.0
 */
@Entity(name = "IndexedNode2")
public class IndexedNode2 {

    @Id
    private Integer id;
    @ManyToOne
    private Root2 parent;
    @Column(name = "list_index")
    private Integer index;
}